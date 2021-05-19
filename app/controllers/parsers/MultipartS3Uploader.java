package controllers.parsers;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import javax.inject.Inject;

import com.typesafe.config.Config;

import play.api.http.HttpConfiguration;
import play.api.http.HttpErrorHandler;
import play.core.parsers.Multipart;
import play.libs.F;
import play.libs.concurrent.HttpExecutionContext;
import play.libs.streams.Accumulator;
import play.mvc.BodyParser;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;

import akka.actor.ActorSystem;
import akka.util.ByteString;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Flow;
import akka.http.javadsl.model.ContentTypes;

import akka.stream.alpakka.s3.S3Ext;
import akka.stream.alpakka.s3.S3Attributes;
import akka.stream.alpakka.s3.S3Settings;
import akka.stream.alpakka.s3.MultipartUploadResult;
import akka.stream.alpakka.s3.javadsl.S3;

import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;

import static controllers.parsers.ByteLimiter.MaxSizeExceeded;


public class MultipartS3Uploader extends BodyParser.DelegatingMultipartFormDataBodyParser<String> {

    private final ActorSystem system;
    private final HttpExecutionContext ec;

    private final long maxLength;

    @Inject
    public MultipartS3Uploader(ActorSystem system, Materializer materializer, HttpConfiguration config, HttpErrorHandler errorHandler, HttpExecutionContext ec) {
        super(materializer, config.parser().maxMemoryBuffer(), errorHandler);

        this.system = system;
        this.ec = ec;

        this.maxLength = config.parser().maxDiskBuffer();
    }

    @Override
    public Accumulator<ByteString, F.Either<Result, Http.MultipartFormData<String>>> apply(Http.RequestHeader request) {
        if (contentLengthHeaderExceedsMaxLength(request, maxLength)) {
            return Accumulator.done(requestEntityTooLarge());
        } else {
            return super.apply(request)
                .through(Flow.of(ByteString.class).via(new ByteLimiter(maxLength)))
                .recover(MultipartS3Uploader::failure, ec.current());
        }
    }

    @Override
    public Function<Multipart.FileInfo, Accumulator<ByteString, Http.MultipartFormData.FilePart<String>>> createFilePartHandler() {
        return (Multipart.FileInfo fileInfo) -> {
            final String filename = fileInfo.fileName();
            final String partname = fileInfo.partName();
            final String dispositionType = fileInfo.dispositionType();
            final String contentType = fileInfo.contentType().getOrElse(() ->  "application/octet-stream");

            String key = UUID.randomUUID().toString();

            S3Settings settings = loadSettings(system);

            final Sink<ByteString, CompletionStage<MultipartUploadResult>> sink = S3.multipartUpload("dev-data", key, ContentTypes.parse(contentType))
                .withAttributes(S3Attributes.settings(settings));

            return Accumulator.fromSink(
                sink.mapMaterializedValue(
                    completionStage ->
                        completionStage.thenApplyAsync(
                            result ->
                                new Http.MultipartFormData.FilePart<>(
                                    partname,
                                    filename,
                                    contentType,
                                    result.getKey()))));
        };
    }

    private static F.Either<Result, Http.MultipartFormData<String>> failure(Throwable throwable) {
        if (throwable.getCause() instanceof MaxSizeExceeded) {
            return requestEntityTooLarge();
        }

        return F.Either.Left(Results.internalServerError(throwable.getMessage()));
    }

    private static F.Either<Result, Http.MultipartFormData<String>> requestEntityTooLarge() {
        return F.Either.Left(Results.status(Http.Status.REQUEST_ENTITY_TOO_LARGE, "Request entity too large"));
    }

    private S3Settings loadSettings(ActorSystem system) {
        Config config = system.settings().config();

        return S3Ext.get(system)
            .settings()
            .withCredentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(config.getString("storage.public.key"), config.getString("storage.public.secret"))))
            .withEndpointUrl(config.getString("storage.public.url"));
    }

    private static boolean contentLengthHeaderExceedsMaxLength(Http.RequestHeader request, long maxLength) {
        return request.header(Http.HeaderNames.CONTENT_LENGTH)
            .filter(value -> value.chars().allMatch(Character::isDigit))
            .map(Long::valueOf)
            .map(contentLength -> contentLength > maxLength)
            .orElse(false);
    }
}
