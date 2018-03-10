package ru.alcereo.processdsl.task;

import akka.actor.Props;
import akka.dispatch.Futures;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.google.common.collect.ImmutableMap;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.Future;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static ru.alcereo.processdsl.Utils.failure;

public class RestSyncTask extends PersistFSMTask{

    private OkHttpClient client;

    public static Props props(UUID taskIdentifier, OkHttpClient client){
        return Props.create(RestSyncTask.class, () -> new RestSyncTask(taskIdentifier, client));
    }

    public RestSyncTask(UUID taskIdentifier, OkHttpClient client) {
        super(taskIdentifier);
        this.client = client;
    }

    @Override
    public void handleExecution(TaskDataState taskStateData) {

        String address = (String) taskStateData.getProperties().get("address"); // "http://localhost:8081/"
        ExecutionContextExecutor ds = getContext().dispatcher();

        log().debug("Start handle execution. address: {}", address);

        Future<String> httpResponseCompletionStage =
                Futures.future(() -> {
                    final Request request = new Request.Builder()
                            .url(address)
                            .get()
                            .build();

                    try(Response execute = client.newCall(request).execute()) {
                        return execute.body().string();
                    }
                }, ds);

        httpResponseCompletionStage
                .map(response -> (Map) ImmutableMap.of("http-response", response), ds)
                .map(AppendToContextCmd::new, ds)
                .flatMap(appendToContextCmd ->{
                    log().debug("Append response to context: {}", appendToContextCmd);
                    return Patterns.ask(
                            getSelf(),
                            appendToContextCmd,
                            Timeout.apply(5, TimeUnit.SECONDS));
                }, ds)
                .flatMap(result -> {
                    log().debug("Send executing finish command to self()");
                    return Patterns.ask(
                            getSelf(),
                            new SuccessFinishExecutingCmd(),
                            Timeout.apply(5, TimeUnit.SECONDS));
                }, ds)
                .onFailure(
                        failure(throwable -> {
                            log().error(throwable, "Error execution task!!");

                            Patterns.ask(
                                    getSelf(),
                                    new ErrorExecutingCmd(throwable),
                                    Timeout.apply(5, TimeUnit.SECONDS)
                            ).onFailure(
                                    failure(throwable1 -> {
                                        throw new RuntimeException("Error execution!!");
                                    }), ds
                            );
                        }), ds);

        log().debug("Handle finish. Now async.");
    }

}
