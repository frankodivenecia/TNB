package org.jboss.fuse.tnb.sql.mssql.resource.openshift;

import org.jboss.fuse.tnb.common.deployment.OpenshiftDeployable;
import org.jboss.fuse.tnb.common.deployment.WithName;
import org.jboss.fuse.tnb.common.openshift.OpenshiftClient;
import org.jboss.fuse.tnb.sql.common.openshift.OpenshiftDB;
import org.jboss.fuse.tnb.sql.mssql.service.MSSQL;

import com.google.auto.service.AutoService;

@AutoService(MSSQL.class)
public class OpenshiftMSSQL extends MSSQL implements OpenshiftDeployable, WithName {
    private final OpenshiftDB openshiftDb = new OpenshiftDB(this, PORT);

    @Override
    public void create() {
        openshiftDb.create();
    }

    @Override
    public void undeploy() {
        openshiftDb.undeploy();
    }

    @Override
    public void openResources() {
        openshiftDb.openResources();
    }

    @Override
    public void closeResources() {
        openshiftDb.closeResources();
    }

    @Override
    public boolean isReady() {
        return openshiftDb.isReady();
    }

    @Override
    public boolean isDeployed() {
        return openshiftDb.isDeployed();
    }

    @Override
    public String hostname() {
        return OpenshiftClient.get().getClusterHostname(name());
    }

    @Override
    public int port() {
        return PORT;
    }

    @Override
    public String name() {
        return "mssql-tnb";
    }
}