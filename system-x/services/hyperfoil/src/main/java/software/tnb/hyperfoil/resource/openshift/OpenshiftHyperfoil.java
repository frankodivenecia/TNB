package software.tnb.hyperfoil.resource.openshift;

import software.tnb.common.deployment.ReusableOpenshiftDeployable;
import software.tnb.common.deployment.WithExternalHostname;
import software.tnb.common.deployment.WithOperator;
import software.tnb.common.openshift.OpenshiftClient;
import software.tnb.common.utils.WaitUtils;
import software.tnb.hyperfoil.service.Hyperfoil;
import software.tnb.hyperfoil.service.HyperfoilConfiguration;
import software.tnb.hyperfoil.validation.generated.ApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auto.service.AutoService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import cz.xtf.core.openshift.helpers.ResourceParsers;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.openshift.client.internal.readiness.OpenShiftReadiness;

@AutoService(Hyperfoil.class)
public class OpenshiftHyperfoil extends Hyperfoil implements ReusableOpenshiftDeployable, WithExternalHostname, WithOperator {

    private static final Logger LOG = LoggerFactory.getLogger(OpenshiftHyperfoil.class);

    private static final String DEFAULT_CHANNEL = "alpha";
    private static final String OPERATOR_NAME = "hyperfoil-bundle";
    private static final String DEFAULT_SOURCE = "community-operators";
    private static final String SUBSCRIPTION_NAME = "tnb-hyperfoil";
    private static final String SUBSCRIPTION_NAMESPACE = "openshift-marketplace";

    private static final String APP_NAME = "hyperfoil-" + OpenshiftClient.get().getNamespace();

    private static final CustomResourceDefinitionContext HYPERFOIL_CTX = new CustomResourceDefinitionContext.Builder()
        .withName("Hyperfoil")
        .withGroup("hyperfoil.io")
        .withVersion("v1alpha2")
        .withPlural("hyperfoils")
        .withScope("Namespaced")
        .build();

    @Override
    public void undeploy() {
        if (!HyperfoilConfiguration.keepRunning()) {
            try {
                OpenshiftClient.get().customResource(HYPERFOIL_CTX).delete(OpenshiftClient.get().getNamespace(), APP_NAME, true);
                WaitUtils.waitFor(() -> OpenshiftClient.get().getLabeledPods(Map.of("kind", HYPERFOIL_CTX.getName(), "app", APP_NAME))
                    .isEmpty(), "Waiting until hyperfoil pods are deleted");
                OpenshiftClient.get().deleteSubscription(SUBSCRIPTION_NAME);
            } catch (IOException e) {
                LOG.error("Error on Hyperfoil deletetion", e);
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void openResources() {
        WaitUtils.waitFor(() -> {
            try {
                return getValidation().getDefaultApi().openApiWithHttpInfo().getStatusCode() == 200;
            } catch (ApiException e) {
                return false;
            }
        }, "Waiting for responding api endpoint");
    }

    @Override
    public void closeResources() {
    }

    @Override
    public void create() {
        LOG.debug("Creating Hyperfoil instance");
        // Create subscription
        OpenshiftClient.get().createSubscription(operatorChannel(), OPERATOR_NAME, operatorCatalog(), SUBSCRIPTION_NAME, SUBSCRIPTION_NAMESPACE);
        OpenshiftClient.get().waitForInstallPlanToComplete(SUBSCRIPTION_NAME);

        try {
            OpenshiftClient.get().customResource(HYPERFOIL_CTX).createOrReplace(getHyperfoilDefinition());
        } catch (IOException e) {
            LOG.error("Error on Hyperfoil creation", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isReady() {
        List<Pod> pods = OpenshiftClient.get().getLabeledPods("app", APP_NAME);
        return pods.size() > 0 && pods.stream().allMatch(OpenShiftReadiness::isPodReady);
    }

    @Override
    public boolean isDeployed() {
        List<Pod> pods = OpenshiftClient.get().pods().withLabel("control-plane", "controller-manager").list().getItems().stream()
            .filter(p -> p.getMetadata().getName().contains("hyperfoil")).collect(Collectors.toList());
        return pods.size() == 1 && ResourceParsers.isPodReady(pods.get(0))
            && ((List) OpenshiftClient.get().customResource(HYPERFOIL_CTX).list().get("items")).size() == 1;
    }

    @Override
    public String connection() {
        return "https://" + hyperfoilUrl() + ":" + getPortMapping(443);
    }

    @Override
    public String hyperfoilUrl() {
        return externalHostname();
    }

    @Override
    public int getPortMapping(int port) {
        return port;
    }

    private Map<String, Object> getHyperfoilDefinition() {
        Map<String, Object> metadata = Map.of("name", APP_NAME
            , "namespace", OpenshiftClient.get().getNamespace());
        Map<String, Object> spec = Map.of("agentDeployTimeout", 120000
            , "version", "latest"
            , "route", Map.of("host", OpenshiftClient.get().generateHostname("hyperfoil")) //"hyperfoil.apps.mycloud.example.com"
        );
        return Map.of("kind", HYPERFOIL_CTX.getName()
            , "apiVersion", String.format("%s/%s", HYPERFOIL_CTX.getGroup(), HYPERFOIL_CTX.getVersion())
            , "metadata", metadata
            , "spec", spec);
    }

    @Override
    public void cleanup() {
        //all benchmarks should be managed by the tests
    }

    @Override
    public String externalHostname() {
        return OpenshiftClient.get().getRoute(APP_NAME).getSpec().getHost();
    }

    @Override
    public String defaultOperatorCatalog() {
        return DEFAULT_SOURCE;
    }

    @Override
    public String defaultOperatorChannel() {
        return DEFAULT_CHANNEL;
    }
}
