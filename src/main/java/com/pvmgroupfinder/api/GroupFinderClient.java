package com.pvmgroupfinder.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.pvmgroupfinder.model.CreateListingRequest;
import com.pvmgroupfinder.model.ChatMessage;
import com.pvmgroupfinder.model.ChatMessageResponse;
import com.pvmgroupfinder.model.GroupListing;
import com.pvmgroupfinder.model.JoinRequest;
import com.pvmgroupfinder.model.JoinRequestResponse;
import com.pvmgroupfinder.model.ListingResponse;
import com.pvmgroupfinder.model.MyGroupResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

@Slf4j
public class GroupFinderClient
{
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;
    private volatile String baseUrl;
    private volatile String accessToken;
    private volatile Call chatStreamCall;

    @Inject
    public GroupFinderClient(OkHttpClient httpClient, Gson gson)
    {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    public void configure(String baseUrl)
    {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public CompletableFuture<Void> openSession(UUID installationId, String installationSecret, String observedRsn)
    {
        JsonObject body = new JsonObject();
        body.addProperty("installationId", installationId.toString());
        body.addProperty("installationSecret", installationSecret);
        if (observedRsn != null)
        {
            body.addProperty("observedRsn", observedRsn);
        }

        Request request = new Request.Builder()
            .url(url("/v1/sessions"))
            .post(RequestBody.create(JSON, gson.toJson(body)))
            .build();

        return execute(request, JsonObject.class).thenAccept(json ->
            accessToken = json.get("accessToken").getAsString());
    }

    public CompletableFuture<List<GroupListing>> getListings(String activity)
    {
        HttpUrl.Builder builder = url("/v1/listings").newBuilder();
        if (activity != null && !"ALL".equals(activity))
        {
            builder.addQueryParameter("activity", activity);
        }

        Request request = authenticatedRequest(builder.build()).get().build();
        return execute(request, ListingResponse.class).thenApply(ListingResponse::getListings);
    }

    public CompletableFuture<GroupListing> createListing(CreateListingRequest listing)
    {
        Request request = authenticatedRequest("/v1/listings")
            .post(RequestBody.create(JSON, gson.toJson(listing)))
            .build();
        return execute(request, GroupListing.class);
    }

    public CompletableFuture<Void> requestJoin(String listingId, String role, String message, int experienceKc)
    {
        JsonObject body = new JsonObject();
        body.addProperty("role", role);
        body.addProperty("message", message);
        body.addProperty("experienceKc", experienceKc);
        Request request = authenticatedRequest("/v1/listings/" + listingId + "/requests")
            .post(RequestBody.create(JSON, gson.toJson(body)))
            .build();
        return execute(request, JsonObject.class).thenApply(ignored -> null);
    }

    public CompletableFuture<List<JoinRequest>> getIncomingRequests()
    {
        Request request = authenticatedRequest("/v1/requests/incoming").get().build();
        return execute(request, JoinRequestResponse.class).thenApply(JoinRequestResponse::getRequests);
    }

    public CompletableFuture<Void> acceptRequest(String requestId)
    {
        return postEmpty("/v1/requests/" + requestId + "/accept");
    }

    public CompletableFuture<Void> rejectRequest(String requestId)
    {
        return postEmpty("/v1/requests/" + requestId + "/reject");
    }

    public CompletableFuture<Void> closeListing(String listingId)
    {
        Request request = authenticatedRequest("/v1/listings/" + listingId).delete().build();
        return executeEmpty(request);
    }

    public CompletableFuture<GroupListing> getMyGroup()
    {
        Request request = authenticatedRequest("/v1/me/group").get().build();
        return execute(request, MyGroupResponse.class).thenApply(MyGroupResponse::getGroup);
    }

    public CompletableFuture<Void> leaveGroup(String listingId)
    {
        Request request = authenticatedRequest("/v1/listings/" + listingId + "/members/me")
            .delete().build();
        return executeEmpty(request);
    }

    public CompletableFuture<Void> setReady(boolean ready)
    {
        JsonObject body = new JsonObject();
        body.addProperty("ready", ready);
        Request request = authenticatedRequest("/v1/me/group/ready")
            .put(RequestBody.create(JSON, gson.toJson(body)))
            .build();
        return execute(request, JsonObject.class).thenApply(ignored -> null);
    }

    public CompletableFuture<List<ChatMessage>> getChatMessages()
    {
        Request request = authenticatedRequest("/v1/me/group/messages").get().build();
        return execute(request, ChatMessageResponse.class).thenApply(ChatMessageResponse::getMessages);
    }

    public CompletableFuture<Void> sendChatMessage(String message)
    {
        JsonObject body = new JsonObject();
        body.addProperty("body", message);
        Request request = authenticatedRequest("/v1/me/group/messages")
            .post(RequestBody.create(JSON, gson.toJson(body)))
            .build();
        return execute(request, ChatMessage.class).thenApply(ignored -> null);
    }

    public CompletableFuture<Void> reportChatMessage(String messageId, String reason, String details)
    {
        JsonObject body = new JsonObject();
        body.addProperty("reason", reason);
        body.addProperty("details", details);
        Request request = authenticatedRequest("/v1/messages/" + messageId + "/report")
            .post(RequestBody.create(JSON, gson.toJson(body)))
            .build();
        return executeEmpty(request);
    }

    public void openChatStream(Consumer<ChatMessage> onMessage, Runnable onClosed)
    {
        cancelChatStream();
        Request request = authenticatedRequest("/v1/me/group/chat-stream")
            .header("Accept", "text/event-stream")
            .get()
            .build();
        Call call = httpClient.newCall(request);
        chatStreamCall = call;
        call.enqueue(new Callback()
        {
            @Override
            public void onFailure(Call failedCall, IOException error)
            {
                finishChatStream(call, onClosed);
            }

            @Override
            public void onResponse(Call responseCall, Response response)
            {
                try (Response closeable = response)
                {
                    if (!response.isSuccessful() || response.body() == null) return;
                    BufferedSource source = response.body().source();
                    while (!source.exhausted())
                    {
                        String line = source.readUtf8Line();
                        if (line != null && line.startsWith("data: ") && line.contains("\"senderRsn\""))
                        {
                            onMessage.accept(gson.fromJson(line.substring(6), ChatMessage.class));
                        }
                    }
                }
                catch (Exception error)
                {
                    log.debug("RaidMates chat stream closed", error);
                }
                finally
                {
                    finishChatStream(call, onClosed);
                }
            }
        });
    }

    private void finishChatStream(Call call, Runnable onClosed)
    {
        if (chatStreamCall == call)
        {
            chatStreamCall = null;
            onClosed.run();
        }
    }

    public void cancelChatStream()
    {
        Call call = chatStreamCall;
        chatStreamCall = null;
        if (call != null) call.cancel();
    }

    private CompletableFuture<Void> postEmpty(String path)
    {
        Request request = authenticatedRequest(path)
            .post(RequestBody.create(JSON, "{}"))
            .build();
        return executeEmpty(request);
    }

    public void cancelAll()
    {
        cancelChatStream();
        httpClient.dispatcher().queuedCalls().stream()
            .filter(this::isOurCall)
            .forEach(Call::cancel);
        httpClient.dispatcher().runningCalls().stream()
            .filter(this::isOurCall)
            .forEach(Call::cancel);
    }

    private boolean isOurCall(Call call)
    {
        return baseUrl != null && call.request().url().toString().startsWith(baseUrl);
    }

    private Request.Builder authenticatedRequest(String path)
    {
        return authenticatedRequest(url(path));
    }

    private Request.Builder authenticatedRequest(HttpUrl requestUrl)
    {
        if (accessToken == null)
        {
            throw new IllegalStateException("No API session");
        }
        return new Request.Builder()
            .url(requestUrl)
            .header("Authorization", "Bearer " + accessToken);
    }

    private HttpUrl url(String path)
    {
        HttpUrl parsed = HttpUrl.parse(baseUrl + path);
        if (parsed == null)
        {
            throw new IllegalArgumentException("Invalid API URL");
        }
        return parsed;
    }

    private <T> CompletableFuture<T> execute(Request request, Class<T> type)
    {
        CompletableFuture<T> future = new CompletableFuture<>();
        httpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException error)
            {
                future.completeExceptionally(error);
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                try (Response closeable = response)
                {
                    if (!response.isSuccessful())
                    {
                        future.completeExceptionally(
                            new IOException("API returned HTTP " + response.code()));
                        return;
                    }
                    if (response.body() == null)
                    {
                        future.completeExceptionally(new IOException("Empty API response"));
                        return;
                    }
                    future.complete(gson.fromJson(response.body().charStream(), type));
                }
                catch (Exception error)
                {
                    log.debug("Unable to parse RaidMates response", error);
                    future.completeExceptionally(error);
                }
            }
        });
        return future;
    }

    private CompletableFuture<Void> executeEmpty(Request request)
    {
        CompletableFuture<Void> future = new CompletableFuture<>();
        httpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException error)
            {
                future.completeExceptionally(error);
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                try (Response ignored = response)
                {
                    if (!response.isSuccessful())
                    {
                        future.completeExceptionally(new IOException("API returned HTTP " + response.code()));
                        return;
                    }
                    future.complete(null);
                }
            }
        });
        return future;
    }
}
