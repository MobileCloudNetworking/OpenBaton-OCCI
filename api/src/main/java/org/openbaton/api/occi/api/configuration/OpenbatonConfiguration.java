package org.openbaton.api.occi.api.configuration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.openbaton.catalogue.mano.descriptor.NetworkServiceDescriptor;
import org.openbaton.catalogue.mano.descriptor.VirtualNetworkFunctionDescriptor;
import org.openbaton.catalogue.nfvo.Location;
import org.openbaton.catalogue.nfvo.VimInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.FileReader;
import java.io.IOException;

/**
 * Created by maa on 13.10.15.
 */
@Configuration
@ComponentScan ("org.openbaton.api.occi.api")
public class OpenbatonConfiguration {

    private static Logger logger = LoggerFactory.getLogger(OpenbatonConfiguration.class);

    @Bean
    public VirtualNetworkFunctionDescriptor getCloudRepository(){
        VirtualNetworkFunctionDescriptor vnfd = null;
        Gson mapper = new GsonBuilder().create();

        try{
            logger.debug("Reading cloud repository");
            FileReader vnfdFile = new FileReader(new ClassPathResource("vnfd/openims_vnfd.json").getFile());
            vnfd = mapper.fromJson(vnfdFile,VirtualNetworkFunctionDescriptor.class);
            logger.debug("CLOUD REPOSITORY IS " + vnfd.toString());

        }
        catch (IOException e){
            logger.debug("DO NOT REMOVE OR RENAME THE FILE $resources/vnfd/openims_vnfd.json!!!!\nexiting");
        }

        return vnfd;
    }

    @Bean
    public NetworkServiceDescriptor getDescriptor(){
        logger.debug("Reading descriptor");
        NetworkServiceDescriptor nsd = new NetworkServiceDescriptor();
        Gson mapper = new GsonBuilder().create();

        try{
            logger.debug("Trying to read the descriptor");
            FileReader nsdFile = new FileReader(new ClassPathResource("nsd/openims_nsd.json").getFile());
            nsd = mapper.fromJson(nsdFile,NetworkServiceDescriptor.class);
            logger.debug("DESCRIPTOR " + nsd.toString());
        }
        catch (IOException e){
            e.printStackTrace();
            logger.debug("DO NOT REMOVE OR RENAME THE FILE $resources/nsd/openims_nsd.json!!!!\nexiting");
        }
        return nsd;
    }

}