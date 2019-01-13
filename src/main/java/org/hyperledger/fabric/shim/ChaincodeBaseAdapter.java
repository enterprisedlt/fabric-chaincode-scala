package org.hyperledger.fabric.shim;

import io.grpc.ManagedChannelBuilder;

import java.io.IOException;

/**
 * <p>
 * This adapter provides access to ChaincodeBase methods with "package level" access (i.e. those with default access modifier)
 *
 * @author Alexey Polubelov
 */
public abstract class ChaincodeBaseAdapter extends ChaincodeBase {

    @Override
    void processEnvironmentOptions() {
        doProcessEnvironmentOptions();
    }

    protected void doProcessEnvironmentOptions() {
        super.processEnvironmentOptions();
    }

    @Override
    void processCommandLineOptions(String[] args) {
        doProcessCommandLineOptions(args);
    }

    protected void doProcessCommandLineOptions(String[] args) {
        super.processCommandLineOptions(args);
    }

    @Override
    void initializeLogging() {
        doInitializeLogging();
    }

    protected void doInitializeLogging() {
        super.initializeLogging();
    }

    @Override
    void validateOptions() {
        doValidateOptions();
    }

    protected void doValidateOptions() {
        super.validateOptions();
    }

    @Override
    ManagedChannelBuilder<?> newChannelBuilder() throws IOException {
        return createGRPCChannelBuilder();
    }

    protected ManagedChannelBuilder<?> createGRPCChannelBuilder() throws IOException {
        return super.newChannelBuilder();
    }
}
