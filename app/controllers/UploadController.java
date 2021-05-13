package controllers;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

import akka.NotUsed;
import akka.japi.Pair;
import akka.actor.ActorSystem;
import akka.stream.alpakka.s3.S3Attributes;
import akka.stream.alpakka.s3.javadsl.S3;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

import play.data.Form;
import play.data.FormFactory;
import play.libs.Json;
import play.mvc.BodyParser;
import play.libs.concurrent.HttpExecutionContext;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import com.typesafe.config.Config;

import akka.stream.alpakka.s3.S3Ext;
import akka.stream.alpakka.s3.ObjectMetadata;
import akka.stream.alpakka.s3.S3Attributes;
import akka.stream.alpakka.s3.S3Settings;
import akka.stream.alpakka.s3.MultipartUploadResult;
import akka.stream.alpakka.s3.javadsl.S3;

import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;

import controllers.parsers.MultipartS3Uploader;

@Singleton
public class UploadController extends Controller {

    private final Config config;
    private final ActorSystem system;
    private final HttpExecutionContext ec;

    @Inject
    public UploadController(Config config, ActorSystem actorSystem, HttpExecutionContext ec) {
        this.config = config;
        this.system = actorSystem;
        this.ec = ec;
    }

    @BodyParser.Of(MultipartS3Uploader.class)
    public CompletionStage<Result> upload(Http.Request request) {
        Http.MultipartFormData.FilePart<String> filePart = request.body().<String>asMultipartFormData().getFile("data");
        if (filePart != null) {
            return CompletableFuture.supplyAsync(() -> ok(filePart.getRef()), ec.current());
        } else {
            return CompletableFuture.supplyAsync(() -> internalServerError("Upload failure"), ec.current());
        }
    }

    public CompletionStage<Result> download(String uuid) {
        S3Settings settings = S3Ext.get(system)
            .settings()
            .withCredentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(config.getString("storage.public.key"), config.getString("storage.public.secret"))))
            .withEndpointUrl(config.getString("storage.public.url"));

        Source<ByteString, NotUsed> source = S3.download("dev-data", uuid)
                .withAttributes(S3Attributes.settings(settings))
                .map(opt -> opt.orElseThrow(() -> new RuntimeException("File not found")))
                .flatMapConcat(Pair::first);

        return CompletableFuture.supplyAsync(() -> ok().chunked(source)
                .withHeader(Http.HeaderNames.CONTENT_DISPOSITION, "attachment; filename=test.zip")
                .as("application/zip"), ec.current());
    }
}
