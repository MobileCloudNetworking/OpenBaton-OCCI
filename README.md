# OCCI-API
### Install
Clone this repository to a location of your choosing. Then compile the
application and it's dependencies:
```bash
./occi-api.sh compile
```

Then set the appropriate settings in ```src/main/resources/application.properties```
and let the init run through:
```bash
./occi-api.sh init
```

### Usage
Start the API with
```bash
./occi-api.sh start
```

### Configuration
Be sure to set the nfvo and occi properties in application.properties

## Update the NSD id
To update the used NSD id from the one set in the config to for example "89ecc3c5-e560-42cb-a06e-c58d3ed4d32e"
```bash
curl -v -X POST http://127.0.0.1:8082/api/v1/occi/nsd -d 89ecc3c5-e560-42cb-a06e-c58d3ed4d32e
```

To check which NSD id is currently set, do a GET without payload to the same URL.

## Instantiate an instance

First of all you need to initialize the API. In a new terminal do get a token from keystone (token must belong to a user which has the admin role for the tenant):

```bash
keystone token-get
export KID='...'
export TENANT='...'
```

Once you have the token you can send the init request to the SO:

```
curl -v -X PUT http://127.0.0.1:8082/api/v1/occi/default
          -H 'Content-Type: text/occi'
          -H 'Category: orchestrator; scheme="http://schemas.mobile-cloud-networking.eu/occi/service#"'
          -H 'X-Auth-Token: '$KID
          -H 'X-Tenant-Name: '$TENANT
```

Trigger the deployment of a service instance:

```
curl -v -X POST http://127.0.0.1:8082/api/v1/occi/default?action=deploy
          -H 'Content-Type: text/occi'
          -H 'Category: deploy; scheme="http://schemas.mobile-cloud-networking.eu/occi/service#"'
          -H 'X-Auth-Token: '$KID
          -H 'X-Tenant-Name: '$TENANT
          -H 'X-OCCI-Attribute: occi.core.id=CHOOSE_AN_IDENTIFIER'
```

Trigger delete of service instance:

```
curl -v -X DELETE http://127.0.0.1:8082/api/v1/occi/default
          -H 'X-Auth-Token: '$KID
          -H 'X-Tenant-Name: '$TENANT
          -H 'X-OCCI-Attribute: occi.core.id=USE_THE_CHOSEN_IDENTIFIER'
```

### Supported by
Open Baton is a project developed by Fraunhofer FOKUS and TU Berlin. It is supported by different European publicly funded projects: 

* [NUBOMEDIA][nubomedia]
* [Mobile Cloud Networking][mcn]
* [CogNet][cognet]

[nubomedia]: https://www.nubomedia.eu/
[mcn]: http://mobile-cloud-networking.eu/site/
[cognet]: http://www.cognet.5g-ppp.eu/cognet-in-5gpp/
