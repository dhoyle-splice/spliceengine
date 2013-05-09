package com.splicemachine.derby.impl.job;

import com.splicemachine.derby.impl.job.coprocessor.CoprocessorTaskScheduler;
import com.splicemachine.derby.impl.job.coprocessor.RegionTask;
import com.splicemachine.derby.utils.ByteDataOutput;
import com.splicemachine.derby.utils.SpliceUtils;
import com.splicemachine.derby.utils.SpliceZooKeeperManager;
import com.splicemachine.job.Status;
import com.splicemachine.utils.SpliceLogUtils;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.ZooKeeperConnectionException;
import org.apache.hadoop.hbase.regionserver.HRegion;
import org.apache.hadoop.hbase.zookeeper.RecoverableZooKeeper;
import org.apache.log4j.Logger;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/**
 * @author Scott Fines
 * Created on: 4/4/13
 */
public abstract class ZooKeeperTask extends DurableTask implements RegionTask {
    private static final long serialVersionUID=3l;
    protected final Logger LOG;
    private int priority;
    protected SpliceZooKeeperManager zkManager;
    protected String jobId;

    protected ZooKeeperTask(){
        super(null);
        this.LOG = Logger.getLogger(this.getClass());
    }

    protected ZooKeeperTask(String jobId,int priority){
        super(null);
        this.priority = priority;
        this.jobId = jobId.replaceAll("/","_");
        this.LOG = Logger.getLogger(this.getClass());
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void prepareTask(HRegion region,
                            SpliceZooKeeperManager zkManager) throws ExecutionException {
        this.zkManager = zkManager;
        if(taskId==null){
            /*
             * If no taskId has been assigned, then this is a new task,
             * and we need to create a Task node and status node for it.
             * Otherwise, it's a re-run of an already initialized task, so we just
             * want to reset to PENDING and resubmit
             */
            this.taskId = buildTaskId(region,jobId ,getTaskType());

            //write out the payload to a durable node
            ByteDataOutput byteOut = new ByteDataOutput();
            try {
                byteOut.writeObject(this);
                byte[] payload = byteOut.toByteArray();

                RecoverableZooKeeper zooKeeper =zkManager.getRecoverableZooKeeper();
                taskId = zooKeeper.create(taskId,payload, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                setTaskId(taskId);
                byte[] statusData = statusToBytes();
                zooKeeper.create(taskId+"/status",statusData,ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.EPHEMERAL);
            } catch (IOException e) {
                throw new ExecutionException(e);
            } catch (InterruptedException e) {
                throw new ExecutionException(e);
            } catch (KeeperException e) {
                throw new ExecutionException(e);
            }
        }else{
            getTaskStatus().setStatus(Status.PENDING);
        }
        //make sure that we haven't been cancelled while we were busy
        checkNotCancelled();
    }

    protected abstract String getTaskType();

    @Override
    public void markCancelled() throws ExecutionException {
        SpliceLogUtils.trace(LOG,"Marking task %s cancelled",taskId);
        markCancelled(true);
    }

    @Override
    public void markStarted() throws ExecutionException, CancellationException {
        SpliceLogUtils.trace(LOG, "Marking task %s started", taskId);
        status.setStatus(Status.EXECUTING);
        updateStatus(true);
        //reset the cancellation watch to notify us if the node is deleted
//        checkNotCancelled();

    }

    @Override
    public void markCompleted() throws ExecutionException {
        SpliceLogUtils.trace(LOG, "Marking task %s completed", taskId);
        status.setStatus(Status.COMPLETED);
        updateStatus(false);

    }

    @Override
    public void markFailed(Throwable error) throws ExecutionException {
        switch (status.getStatus()) {
            case INVALID:
            case FAILED:
            case COMPLETED:
                SpliceLogUtils.warn(LOG,"Received task error after entering "+status.getStatus()+" state, ignoring",error);
                return;
        }

        SpliceLogUtils.trace(LOG,"Marking task %s failed",taskId);
        status.setError(error);
        status.setStatus(Status.FAILED);
        updateStatus(false);
    }

    @Override
    public void markInvalid() throws ExecutionException {
        SpliceLogUtils.trace(LOG,"Marking task %s invalid",taskId);
        status.setStatus(Status.INVALID);
        updateStatus(false);
    }

    @Override
    public void updateStatus(final boolean cancelOnError) throws CancellationException, ExecutionException {
        assert zkManager!=null;
        try{
            final byte[] status = statusToBytes();
            zkManager.executeUnlessExpired(new SpliceZooKeeperManager.Command<Void>() {
                @Override
                public Void execute(RecoverableZooKeeper zooKeeper) throws InterruptedException, KeeperException {
                    SpliceLogUtils.trace(LOG,"Attempting to set status %s for task %s",ZooKeeperTask.this.status.getStatus(),taskId);
                    zooKeeper.setData(taskId + "/status", status, -1);
                    return null;
                }
            });
        } catch (InterruptedException e) {
            throw new ExecutionException(e);
        } catch (KeeperException e) {
            if(e.code()==KeeperException.Code.NONODE && cancelOnError){
                status.setStatus(Status.CANCELLED);
                throw new CancellationException();
            }else
                throw new ExecutionException(e);
        } catch (IOException e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public void cleanup() throws ExecutionException {
        //delete the task node and status node
        try {
            zkManager.execute(new SpliceZooKeeperManager.Command<Void>() {
                @Override
                public Void execute(RecoverableZooKeeper zooKeeper) throws InterruptedException, KeeperException {
                    zooKeeper.delete(taskId+"/status",-1);
                    zooKeeper.delete(taskId,-1);

                    return null;
                }
            });
        } catch (InterruptedException e) {
            throw new ExecutionException(e);
        } catch (KeeperException e) {
            if(e.code()!= KeeperException.Code.NONODE)
                throw new ExecutionException(e);
        }
    }

    private void markCancelled(boolean propagate) throws ExecutionException{
        SpliceLogUtils.trace(LOG,"cancelling task %s "+(propagate ? ", propagating cancellation state":"not propagating state"),taskId);
        switch (status.getStatus()) {
            case FAILED:
            case COMPLETED:
            case CANCELLED:
                return; //nothing to do
        }

        status.setStatus(Status.CANCELLED);
        if(propagate)
            updateStatus(false);
    }

    protected String buildTaskId(HRegion region,String jobId,String taskType) {
        HRegionInfo regionInfo = region.getRegionInfo();
        return CoprocessorTaskScheduler.getRegionQueue(regionInfo)+"_"+jobId+"_"+taskType+"-"+ SpliceUtils.getUniqueKeyString();
    }

    private void checkNotCancelled()throws ExecutionException {
        SpliceLogUtils.trace(LOG,"Attaching existence watcher to job %s",jobId);
        //call exists() on status to make sure that we notice cancellations
        Stat stat;
        try {
            stat = zkManager.getRecoverableZooKeeper().exists(CoprocessorTaskScheduler.getJobPath() + "/" + jobId, new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    SpliceLogUtils.trace(LOG, "Received WatchedEvent %s on node %s",event.getType(),event.getPath());
                    if (event.getType() != Event.EventType.NodeDeleted)
                        return; //nothing to do

                    //if we are in a terminal state, then don't cancel
                    switch (status.getStatus()) {
                        case FAILED:
                        case COMPLETED:
                        case CANCELLED:
                            return;
                    }

                    try {
                        markCancelled(false);
                    } catch (ExecutionException ee) {
                        SpliceLogUtils.error(LOG, "Unable to cancel task with id " + getTaskId(), ee.getCause());
                    }
                }
            });
            if(stat==null){
                //we've already been cancelled!
                markCancelled(false);
            }
        } catch (KeeperException e) {
            throw new ExecutionException(e);
        } catch (InterruptedException e) {
            throw new ExecutionException(e);
        } catch (ZooKeeperConnectionException e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        jobId = in.readUTF();
        priority = in.readInt();
        if(in.readBoolean())
            taskId = in.readUTF();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(jobId);
        out.writeInt(priority);
        out.writeBoolean(taskId!=null);
        if(taskId!=null)
            out.writeUTF(taskId);
    }
}
