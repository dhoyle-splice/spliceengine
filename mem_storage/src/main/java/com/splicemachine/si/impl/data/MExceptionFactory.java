package com.splicemachine.si.impl.data;

import com.splicemachine.si.api.data.ExceptionFactory;
import com.splicemachine.si.api.txn.Txn;
import com.splicemachine.si.impl.MCannotCommitException;
import com.splicemachine.si.impl.MTransactionTimeout;
import com.splicemachine.si.impl.MWriteConflict;

import java.io.IOException;

/**
 * @author Scott Fines
 *         Date: 12/16/15
 */
public class MExceptionFactory implements ExceptionFactory{
    public static final ExceptionFactory INSTANCE= new MExceptionFactory();

    protected MExceptionFactory(){}

    @Override
    public IOException cannotCommit(String message){
        return new MCannotCommitException(message);
    }

    @Override
    public boolean allowsRetry(Throwable error){
        return false;
    }

    @Override
    public IOException writeWriteConflict(long txn1,long txn2){
        return new MWriteConflict(txn1,txn2);
    }

    @Override
    public IOException readOnlyModification(String message){
        return new MReadOnly(message);
    }

    @Override
    public IOException noSuchFamily(String message){
        return new MNoSuchFamily(message);
    }

    @Override
    public IOException transactionTimeout(long tnxId){
        return new MTransactionTimeout(tnxId);
    }

    @Override
    public IOException cannotCommit(long txnId,Txn.State actualState){
        return new MCannotCommitException(txnId,actualState);
    }

    @Override
    public IOException callerDisconnected(String message){
        return new MCallerDisconnected(message);
    }

    @Override
    public IOException failedServer(String message){
        return new MFailedServer(message);
    }


    @Override
    public IOException notServingPartition(String s){
        return new MNotServingPartition(s);
    }

    @Override
    public IOException additiveWriteConflict(){
        return new MAdditiveWriteConflict();
    }

    @Override
    public IOException doNotRetry(String message){
        return new IOException(message);
    }

    @Override
    public IOException processRemoteException(Throwable t){
        if(t instanceof IOException) return (IOException)t;
        else return new IOException(t);
    }

    @Override
    public IOException doNotRetry(Throwable t){
        return new IOException(t);
    }
}
