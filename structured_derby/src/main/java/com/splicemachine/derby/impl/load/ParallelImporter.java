package com.splicemachine.derby.impl.load;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.splicemachine.constants.SpliceConstants;
import com.splicemachine.derby.hbase.SpliceDriver;
import com.splicemachine.derby.utils.marshall.*;
import com.splicemachine.hbase.writer.*;
import com.splicemachine.stats.*;
import com.splicemachine.stats.util.Folders;
import com.splicemachine.utils.IntArrays;
import com.splicemachine.utils.Snowflake;
import com.splicemachine.utils.SpliceLogUtils;
import org.apache.derby.iapi.sql.execute.ExecRow;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author Scott Fines
 * Created on: 9/30/13
 */
public class ParallelImporter implements Importer{
    private static final Logger LOG = Logger.getLogger(ParallelImporter.class);
    private final ExecutorService processingPool;
    private final BlockingQueue<String[]> processingQueue;
    private volatile boolean closed;
    private final ImportContext importContext;
    private final List<Future<Pair<WriteStats,TimeView>>> futures;
    private volatile Exception error;
		private final MetricFactory metricFactory;
		private List<Pair<WriteStats,TimeView>> stats;


		public ParallelImporter(ImportContext importContext,ExecRow template,String txnId) {
        this(importContext,
                template,
                txnId,
                SpliceConstants.maxImportProcessingThreads,
                SpliceConstants.maxImportReadBufferSize,
                SpliceDriver.driver().getTableWriter());
    }

		public ParallelImporter(ImportContext importContext,
														ExecRow template,
														int numProcessingThread,
														int maxImportReadBufferSize,
														String txnId) {
				this(importContext,
								template,
								txnId,
								numProcessingThread,
								maxImportReadBufferSize,
								SpliceDriver.driver().getTableWriter());
		}

    public ParallelImporter(ImportContext importCtx,
                            ExecRow template,
                            String txnId,
                            int numProcessingThreads,
                            int maxImportReadBufferSize,
                            CallBufferFactory<KVPair> factory){
        if (LOG.isTraceEnabled())
            SpliceLogUtils.trace(LOG, "ThreadingCallBuffer#init called");
        this.importContext = importCtx;
        Path filePath = importContext.getFilePath();
        ThreadFactory processingFactory = new ThreadFactoryBuilder()
                .setNameFormat("import-"+filePath.getName()+"-processor-%d")
                .build();
        processingPool = Executors.newFixedThreadPool(numProcessingThreads, processingFactory);

        processingQueue = new BoundedConcurrentLinkedQueue<String[]>(maxImportReadBufferSize);

				if(importCtx.shouldRecordStats()){
						metricFactory = Metrics.basicMetricFactory();
				}else
						metricFactory = Metrics.noOpMetricFactory();

        String tableName = importContext.getTableName();
        futures = Lists.newArrayList();
				/*
				 * We need a write configuration that will not attempt to retry any writes if the task
				 * itself has failed.
				 */
				Writer.WriteConfiguration writeConfiguration = new ForwardingWriteConfiguration(factory.defaultWriteConfiguration()){
						@Override
						public Writer.WriteResponse globalError(Throwable t) throws ExecutionException {
								if(LOG.isTraceEnabled())
										SpliceLogUtils.trace(LOG,"Received a global write error: ",t);
								if(isFailed())
										return Writer.WriteResponse.IGNORE; //we're already dead, so stop writing
								else
										return super.globalError(t);
						}

						@Override
						public Writer.WriteResponse partialFailure(BulkWriteResult result, BulkWrite request) throws ExecutionException {
								if(isFailed())
										return Writer.WriteResponse.IGNORE; //we're already dead, so stop writing
								else
										return super.partialFailure(result, request);
						}

						@Override public MetricFactory getMetricFactory() { return metricFactory; }
				};
        for(int i=0;i<numProcessingThreads;i++){
						RecordingCallBuffer<KVPair> writeDest = factory.writeBuffer(tableName.getBytes(),txnId,writeConfiguration);
            futures.add(processingPool.submit(new Processor(template.getClone(), processingQueue, writeDest)));
        }
    }
    
    @Override
    public void process(String[] parsedRow) throws Exception {
        boolean shouldContinue;
        do{
            /*
             * In the event of a processor failing, it will set the error
             * field atomically, then stop processing entries off the queue. All
             * other processor threads will then atomically see the error, and stop
             * processing themselves. If this happens while we are in the process
             * of offering entries to the processors, then we must be sure that we
             * don't spin for forever. To prevent that, we look at the error field
             * ourselves.
             */
            if(error!=null)
                throw error;
            shouldContinue = !processingQueue.offer(parsedRow,200,TimeUnit.MILLISECONDS);
        }while(shouldContinue);
    }

    @Override
    public void close() throws IOException {
        closed = true;
        try{
						stats = Lists.newArrayListWithCapacity(futures.size());
            for(Future<Pair<WriteStats,TimeView>> future:futures){
                try {
                    stats.add(future.get());
                } catch (InterruptedException e) {
										LOG.warn("Interrupted attempting to complete writes",e);
										Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
										throw new IOException(e.getCause());
                }
            }
						if(isFailed())
								throw new IOException(error);
        }finally{
            processingPool.shutdownNow();
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

		public WriteStats getWriteStats(){
				if(stats==null) return WriteStats.NOOP_WRITE_STATS;

				MergingWriteStats view = new MergingWriteStats(metricFactory);
				for(Pair<WriteStats,TimeView> ioStats:stats){
					view.merge(ioStats.getFirst());
				}
				return view;
		}

		@Override
		public TimeView getTotalTime() {
				if(stats==null) return Metrics.noOpTimeView();

				MultiTimeView multiTimeView = new SimpleMultiTimeView(Folders.sumFolder(),Folders.sumFolder(),
								Folders.sumFolder(),Folders.minLongFolder(),Folders.maxLongFolder());
				for(Pair<WriteStats,TimeView> ioStats:stats){
						multiTimeView.update(ioStats.getSecond());
				}
				return multiTimeView;
		}

		void markFailed(Exception e){ this.error = e; }
    boolean isFailed(){ return error!=null; }

    public PairEncoder newEntryEncoder(ExecRow row) {
        int[] pkCols = importContext.getPrimaryKeys();

				KeyEncoder encoder;
				if(pkCols!=null&& pkCols.length>0){
					encoder = new KeyEncoder(NoOpPrefix.INSTANCE,BareKeyHash.encoder(pkCols,null),NoOpPostfix.INSTANCE);
				}else
					encoder = new KeyEncoder(new SaltedPrefix(getRandomGenerator()),NoOpDataHash.INSTANCE,NoOpPostfix.INSTANCE);

				DataHash rowHash = new EntryDataHash(IntArrays.count(row.nColumns()),null);

				return new PairEncoder(encoder,rowHash, KVPair.Type.INSERT);
    }

		protected Snowflake.Generator getRandomGenerator(){
				return SpliceDriver.driver().getUUIDGenerator().newGenerator(100);
		}

    private class Processor implements Callable<Pair<WriteStats,TimeView>>{
        private final BlockingQueue<String[]> queue;
        private final RecordingCallBuffer<KVPair> writeDestination;
        private final PairEncoder entryEncoder;

        private RowParser importProcessor;

				private Timer writeTimer;

        private Processor(ExecRow row,
                          BlockingQueue<String[]> queue,
                          RecordingCallBuffer<KVPair> writeDestination ){
            this.queue = queue;
            this.writeDestination = writeDestination;
            this.entryEncoder = newEntryEncoder(row);
            this.importProcessor = new RowParser(row, importContext);
						this.writeTimer = metricFactory.newTimer();
        }

        @Override
        public Pair<WriteStats,TimeView> call() throws Exception {
            if (LOG.isTraceEnabled())
                SpliceLogUtils.trace(LOG, "Processor#call");
            /*
             * We eat off the queue, process, and place them into the write destination until
             * we are out of recrods to process.
             *
             * But how do we determine if we are out of records? We can't just pull off the queue
             * until we don't get anything--we may not get anything because the write portion is faster
             * than the read portion, so our threads are running out of work to do. On the other hand,
             * we can't just take() forever, because we'll never shut down that way.
             *
             * So first we break it into two stages--before close() is called on the source, and after.
             * Before closed is called, we block until new records are retained. But we can't block forever,
             * lest we fail to stop if a closed is called without our ever getting a new record to process. Thus,
             * we take for only a little while before backing off and trying again.
             */
            try{
                while(!isClosed()&&!isFailed()){
                    String[] elements = queue.poll(200,TimeUnit.MILLISECONDS);
                    if(elements==null)continue; //try again
                    doImportRow(elements);
								}
								//source is closed, poll until the queue is empty
								String[] next;
								while(!isFailed() && (next = queue.poll())!=null){
										doImportRow(next);
								}
						}
            catch(Exception e){
                markFailed(e);
                throw e;
            } finally{
                if (LOG.isTraceEnabled())
                    SpliceLogUtils.trace(LOG, "Closing Call Buffer");
                writeDestination.close(); //ensure your writes happen
            }
						return Pair.newPair(writeDestination.getWriteStats(), writeTimer.getTime());
        }

        protected void doImportRow(String[] line) throws Exception {
						writeTimer.startTiming();
						ExecRow newRow = importProcessor.process(line, importContext.getColumnInformation());

						writeDestination.add(entryEncoder.encode(newRow));
						writeTimer.tick(1);
        }
    }
}
