package org.openbaton.api.occi.api.controllers;

import org.openbaton.api.occi.api.OcciResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * REST-API to respond to OCCI-conform requests.
 *
 * Maps init, deploy, provision and delete to the corresponding
 * functionality.
 *
 * @author pku
 */

@RestController
@RequestMapping("/api/v1/occi")
public class OcciControler {

    private static final Logger log = LoggerFactory.getLogger(OcciControler.class);

    @RequestMapping(value = "/default", method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OcciResponse init(@RequestHeader HttpHeaders headers) {
        log.debug("Received init request");
        return new OcciResponse("init");
    }

    @RequestMapping(value = "/default", method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OcciResponse deployAndProvision(@RequestParam(value = "action", defaultValue = "") String action,
                                           @RequestHeader HttpHeaders headers, HttpServletResponse response) {
        if (action.equals("deploy")) {
            log.debug("Received deploy request");
            response.setHeader("location", "http://127.0.0.1");

            return new OcciResponse("deploy");
        }

        log.debug("Received provison request");
        List<String> occiAttributes = headers.get("X-OCCI-Attribute");
        occiAttributes.forEach(System.out::println);

        return new OcciResponse("provision");
    }

    @RequestMapping(value = "/default", method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OcciResponse dispose() {
        log.debug("Received delete request");
        return new OcciResponse("delete");
    }
}
