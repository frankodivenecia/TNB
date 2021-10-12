package org.jboss.fuse.tnb.servicenow.validation;

import org.jboss.fuse.tnb.common.utils.HTTPUtils;
import org.jboss.fuse.tnb.servicenow.account.ServiceNowAccount;
import org.jboss.fuse.tnb.servicenow.dto.Incident;
import org.jboss.fuse.tnb.servicenow.dto.IncidentRecordList;
import org.jboss.fuse.tnb.servicenow.dto.IncidentSingleResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.Credentials;

public class ServiceNowValidation {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceNowValidation.class);

    private ServiceNowAccount account;
    private ObjectMapper om;

    public ServiceNowValidation(ServiceNowAccount account) {
        this.account = account;
        om = new ObjectMapper();
        om.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    public List<Incident> getIncidents(int limit) {
        return getFilteredIncidents(null, limit);
    }

    public List<Incident> getFilteredIncidents(String filter, int limit) {

        String getUrl = account.url() + "?sysparm_limit=" + limit;
        if (filter != null) {
            try {
                getUrl += "&sysparm_query=" + URLEncoder.encode(filter, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
        HTTPUtils.Response r = HTTPUtils.get(getUrl, Map.of("Authorization", Credentials.basic(account.userName(), account.password())));
        return parseResponse(r.getBody());
    }

    public void deleteIncident(String incidentId) {
        HTTPUtils.delete(
            String.format("%s/%s", account.url(), incidentId),
            Map.of("Authorization", Credentials.basic(account.userName(), account.password()))
        );
        LOG.debug("Deleted serviceNow incident with id: {} ", incidentId);
    }

    /**
     * Service-Now API returns single object when there is 1 record, but list of records when there are more records.
     *
     * @param response json response
     * @return list of records
     */
    private List<Incident> parseResponse(String response) {

        List<Incident> incidents = new ArrayList<>();
        try {
            IncidentRecordList irl = om.readValue(response, IncidentRecordList.class);
            incidents.addAll(irl.getRecords());
            return incidents;
        } catch (IOException e) {
            // Try to parse it as single record response
            try {
                incidents.add(om.readValue(response, IncidentSingleResponse.class).getRecord());
                return incidents;
            } catch (IOException e1) {
                LOG.error("Unable to unmarshall incident response", e);
            }
        }
        return null;
    }
}