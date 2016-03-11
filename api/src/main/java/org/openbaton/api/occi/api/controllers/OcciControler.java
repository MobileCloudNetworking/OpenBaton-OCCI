package org.openbaton.api.occi.api.controllers;

import org.openbaton.api.occi.api.OpenbatonEvent;
import org.openbaton.api.occi.api.OpenbatonManager;
import org.openbaton.api.occi.api.configuration.NfvoProperties;
import org.openbaton.api.occi.api.configuration.OcciProperties;
import org.openbaton.catalogue.mano.common.Ip;
import org.openbaton.catalogue.mano.descriptor.NetworkServiceDescriptor;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.catalogue.nfvo.EndpointType;
import org.openbaton.catalogue.nfvo.EventEndpoint;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * REST-API to respond to OCCI-conform requests.
 * <p>
 * Maps init, deploy, provision and delete to the corresponding
 * functionality.
 *
 * @author pku
 */

@RestController
@RequestMapping("/api/v1/occi")
public class OcciControler {

    @Autowired private OpenbatonManager obManager;
    @Autowired private OcciProperties occiProperties;
    @Autowired private NfvoProperties nfvoProperties;
    private Logger log;
    private NFVORequestor nfvoRequestor;
    private NetworkServiceDescriptor nsd;
    private NetworkServiceRecord nsr;
    private EventEndpoint receivedEndpointCreation;
    private EventEndpoint receivedEndpointError;
    private List<VirtualNetworkFunctionRecord> virtualNFRs;
    private String deployStatus;
    private String stackId;
    private String apiPath;

    @PostConstruct
    private void init() {
        this.log = LoggerFactory.getLogger(this.getClass());
        this.nfvoRequestor = new NFVORequestor(nfvoProperties.getOpenbatonUsername(), nfvoProperties.getOpenbatonPasswd(), nfvoProperties.getOpenbatonIP(), nfvoProperties.getOpenbatonPort(), "1");
        this.deployStatus = "";
        this.apiPath = "/api/v1/occi/";
        this.stackId = "N/A";
    }

    @RequestMapping(value = "/default", method = RequestMethod.PUT, produces = "text/plain")
    @ResponseStatus(HttpStatus.CREATED)
    public String init(@RequestHeader HttpHeaders headers, HttpServletResponse response) {
        log.debug("Received init request");
        if (!deployStatus.startsWith("CREATE") && !deployStatus.startsWith("DELETE")) {
            deployStatus = "INIT_IN_PROGRESS";

            try {
                nsd = obManager.getNSD();
            } catch (SDKException | ClassNotFoundException e) {
                e.printStackTrace();
            }

            deployStatus = "INIT_COMPLETE";
        }
        response.setHeader("Location", occiProperties.getInternalURL() + ":" + occiProperties.getPort() + "/api/v1/occi/default");
        return "OK";
    }

    @RequestMapping(value = "/default", method = RequestMethod.POST, produces = "text/plain")
    @ResponseStatus(HttpStatus.CREATED)
    public String deployAndProvision(@RequestParam(value = "action", defaultValue = "") String action,
                                     @RequestHeader HttpHeaders headers, HttpServletResponse response) {

        if (action.equals("deploy") && headers.get("Category").get(0).startsWith("deploy;")) {
            if (nsr == null && nsd != null) {
                log.debug("Received deploy request");
                deployStatus = "CREATE_IN_PROGRESS";
                stackId = "1";

                try {
                    // Start the deploy
                    nsr = obManager.deployNSD(nsd.getId());
                    log.debug("Deployed NSR with id " + nsd.getId());
                    String callbackUrl = occiProperties.getInternalURL() + ":" + occiProperties.getPort();

                    // Create callback points for finish or error on instantiate
                    EventEndpoint eventEndpointCreation = new EventEndpoint();
                    eventEndpointCreation.setType(EndpointType.REST);
                    eventEndpointCreation.setEndpoint(callbackUrl + apiPath + nsr.getId());
                    eventEndpointCreation.setEvent(Action.INSTANTIATE_FINISH);
                    eventEndpointCreation.setNetworkServiceId(nsr.getId());

                    EventEndpoint eventEndpointError = new EventEndpoint();
                    eventEndpointError.setType(EndpointType.REST);
                    eventEndpointError.setEndpoint(callbackUrl + apiPath + nsr.getId());
                    eventEndpointError.setEvent(Action.ERROR);
                    eventEndpointError.setNetworkServiceId(nsr.getId());

                    log.debug("Created listening endpoints for INSTANTIATE_FINISH and ERROR");
                    receivedEndpointCreation = this.nfvoRequestor.getEventAgent().create(eventEndpointCreation);
                    receivedEndpointError = this.nfvoRequestor.getEventAgent().create(eventEndpointError);

                } catch (SDKException e) {
                    deployStatus = "CREATE_FAILED";
                    e.printStackTrace();
                }
                return "OK";
            } else {
                // TODO: set 200 statuscode
                return null;
            }
        }

        log.debug("Received provison request");
        List<String> occiAttributes = headers.get("X-OCCI-Attribute");

        return "OK";
    }

    @RequestMapping(value = "/default", method = RequestMethod.DELETE, produces = "text/plain")
    @ResponseStatus(HttpStatus.OK)
    public String dispose() {
        if (nsr != null) {
            try {
                log.debug("Received delete request");
                deployStatus = "DELETE_IN_PROGRESS";

                String nsrId = nsr.getId();
                obManager.disposeNSR(nsrId);

                if (receivedEndpointCreation != null && receivedEndpointError != null) {
                    // In the case that the dispose gets issued before the instantiate finished
                    log.debug("Deleting listening-events");
                    nfvoRequestor.getEventAgent().delete(receivedEndpointCreation.getId());
                    nfvoRequestor.getEventAgent().delete(receivedEndpointError.getId());
                }

                nsr = null;
                log.debug("Deletion of NSR with ID " + nsrId + "successfull!");
                deployStatus = "DELETE_COMPLETE";
                stackId = "N/A";
            } catch (SDKException e) {
                deployStatus = "DELETE_FAILED";
                e.printStackTrace();
            }
            return "OK";
        } else {
            // TODO: set 404 statuscode
            return null;
        }
    }

    @RequestMapping(value = "/default", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public String status(HttpServletResponse response) throws SDKException {
        if (Objects.equals(deployStatus, "CREATE_COMPLETE")) {
            for (VirtualNetworkFunctionRecord record : virtualNFRs) {
                // TODO: proper formating, this may also go in the return header!
                String attributePrefix = occiProperties.getPrefix() + ".";
                String endpoint = "";

                for (VirtualDeploymentUnit vdu: record.getVdu()) {
                    for (VNFCInstance vnfc: vdu.getVnfc_instance()) {
                        // Get all private Ip's
                        for (Ip privateIp : vnfc.getIps()) {
                            response.addHeader("X-OCCI-Attribute", attributePrefix + record.getName() + "." + privateIp.getNetName() + ".private=\"" + privateIp.getIp() + "\"");
                            endpoint = "\"" + privateIp.getIp() + "\"";
                        }
                        // Get all public Ip's
                        for (Ip publicIp : vnfc.getFloatingIps()) {
                            response.addHeader("X-OCCI-Attribute", attributePrefix + record.getName() + "." + publicIp.getNetName() + ".public=\"" + publicIp.getIp() + "\"");
                            endpoint = "\"" + publicIp.getIp() + "\"";
                        }
                    }
                }
                // Set "endpoint" Ip, private if no public Ip's were found, public otherwise.
                response.addHeader("X-OCCI-Attribute", attributePrefix + record.getName() + "=" + endpoint);
            }
        }

        if (!Objects.equals(deployStatus, "")) {
            response.addHeader("X-OCCI-Attribute", "occi.stack.state=\"" + deployStatus + "\"");
        }
        response.addHeader("X-OCCI-Attribute", "occi.stack.id=\"" + "1\"");
        response.addHeader("X-OCCI-Attribute", "occi.core.id=\"" + apiPath + "default\"");
        return "OK";
    }

    @RequestMapping(value = "{id}", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public void setStatus(@RequestBody OpenbatonEvent evt, @PathVariable("id") String id) throws SDKException {
        log.debug("Received event for nsr " + id);

        if (evt.getAction().equals(Action.INSTANTIATE_FINISH)) {
            log.debug("Instantiate finished");
            virtualNFRs = obManager.statusOfNSR(nsr.getId());
            deployStatus = "CREATE_COMPLETE";
        } else if (evt.getAction().equals(Action.ERROR)) {
            log.debug("Error on instantiate");
            deployStatus = "CREATE_FAILED";
        }

        log.debug("Deleting listening-events");
        nfvoRequestor.getEventAgent().delete(receivedEndpointCreation.getId());
        nfvoRequestor.getEventAgent().delete(receivedEndpointError.getId());
    }
}
