package com.github.dedis.student20_pop.utility.json;

import com.github.dedis.student20_pop.model.network.level.high.Message;
import com.github.dedis.student20_pop.model.network.level.low.ChanneledMessage;
import com.github.dedis.student20_pop.model.network.level.low.result.Result;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public final class JsonUtils {

    public static final String JSON_RPC = "jsonrpc";
    public static final String JSON_RPC_VERSION = "2.0";

    public static final String JSON_REQUEST_ID = "id";

    private JsonUtils() {
    }

    public static void testRPCVersion(JsonObject object) throws JsonParseException {
        if (!object.has(JSON_RPC))
            throw new JsonParseException("Unable to find jsonrpc version");

        testRPCVersion(object.get(JSON_RPC).getAsString());
    }

    public static void testRPCVersion(String version) throws JsonParseException {
        if (!version.equals(JSON_RPC_VERSION))
            throw new JsonParseException("Unable to parse jsonrpc version : " + version);
    }

    public static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(ChanneledMessage.class, new JsonLowMessageSerializer())
                .registerTypeAdapter(Message.class, new JsonMessageSerializer())
                .registerTypeAdapter(Result.class, new JsonResultSerializer())
                .create();
    }
}