package com.jsonrpclib.controllers;

import com.google.gson22.GsonBuilder;
import com.google.gson22.reflect.TypeToken;
import com.google.gson22.stream.JsonReader;
import com.jsonrpclib.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: jbogacki
 * Date: 23.07.2013
 * Time: 23:02
 * To change this template use File | Settings | File Templates.
 */
public class JsonRpc2BatchController extends JsonRpc2Controller {

    public JsonRpc2BatchController() {
    }

    public JsonRpc2BatchController(GsonBuilder builder) {
        super(builder);
    }

    @Override
    public RequestInfo createRequest(String url, List<JsonRequest> requests, String apiKey) {
        int i = 0;
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.url = url;
        Object[] requestsJson = new Object[requests.size()];
        for (JsonRequest request : requests) {
            requestsJson[i] = createRequest(url, request, apiKey).postRequest;
            i++;
        }
        requestInfo.postRequest = requestsJson;
        return requestInfo;
    }

    @Override
    public boolean isBatchSupported() {

        return true;
    }

    @Override
    public List<JsonResult> parseResponses(List<JsonRequest> requests, InputStream stream, int debugFlag, JsonTimeStat timeStat) throws Exception {
        List<JsonRpcResponseModel2> responses = null;

        if ((debugFlag & JsonRpc.RESPONSE_DEBUG) > 0) {

            String resStr = convertStreamToString(stream);
            timeStat.tickReadTime();
            longLog("RES(" + resStr.length() + ")", resStr);
            responses = gson.fromJson(resStr,
                    new TypeToken<List<JsonRpcResponseModel2>>() {
                    }.getType());
        } else {
            JsonReader reader = new JsonReader(new InputStreamReader(stream, "UTF-8"));
            responses = gson.fromJson(reader,
                    new TypeToken<List<JsonRpcResponseModel2>>() {
                    }.getType());
            reader.close();
            timeStat.tickReadTime();

        }

        if (responses == null) {
            throw new JsonException("Empty response.");
        }

        Collections.sort(responses);
        Collections.sort(requests, new Comparator<JsonRequest>() {
            @Override
            public int compare(JsonRequest lhs, JsonRequest rhs) {
                return lhs.getId().compareTo(rhs.getId());
            }
        });

        List<JsonResult> finalResponses = new ArrayList<JsonResult>(responses.size());


        for (int i = 0; i < responses.size(); i++) {
            JsonRpcResponseModel2 res = responses.get(i);
            if (res.error == null) {
                Object result = null;
                if (!requests.get(i).getReturnType().equals(Void.TYPE)) {
                    result = gson.fromJson(res.result, requests.get(i).getReturnType());
                }
                finalResponses.add(new JsonResult(res.id, result));
            } else {
                finalResponses.add(new JsonResult(res.id, new JsonException(res.error.message, res.error.code)));
            }
        }

        timeStat.tickParseTime();

        return finalResponses;
    }


}