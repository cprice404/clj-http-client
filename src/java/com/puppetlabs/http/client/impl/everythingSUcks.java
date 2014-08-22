package com.puppetlabs.http.client.impl;

import com.puppetlabs.http.client.RequestOptions;
import com.puppetlabs.http.client.ResponseBodyType;
import com.puppetlabs.http.client.SyncHttpClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by cprice on 8/21/14.
 */
public class everythingSUcks {

    public static void main(String[] args) throws URISyntaxException {
        System.out.println("hi");
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Accept", "text/yaml");
        RequestOptions request_options =
                new RequestOptions(new URI("https://pe-latest.dev:443/nodes/pe-latest.dev"))
                .setHeaders(headers)
                .setSslCaCert("/home/cprice/work/jvm-puppet/scratch/pe-certs/ssl/certs/ca.pem")
                .setSslCert("/home/cprice/work/jvm-puppet/scratch/pe-certs/ssl/certs/pe-latest.dev.pem")
                .setSslKey("/home/cprice/work/jvm-puppet/scratch/pe-certs/ssl/private_keys/pe-latest.dev.pem")
                .setAs(ResponseBodyType.TEXT);

        SyncHttpClient.get(request_options);
    }
}
