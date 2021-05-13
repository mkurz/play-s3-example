package controllers.parsers;

import akka.util.ByteString;
import akka.stream.Attributes;
import akka.stream.FlowShape;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;
import akka.stream.stage.AbstractInHandler;
import akka.stream.stage.AbstractOutHandler;


public class ByteLimiter extends GraphStage<FlowShape<ByteString, ByteString>> {

  private final long maximumBytes;

  public Inlet<ByteString> in = Inlet.<ByteString>create("ByteLimiter.in");
  public Outlet<ByteString> out = Outlet.<ByteString>create("ByteLimiter.out");
  private FlowShape<ByteString, ByteString> shape = FlowShape.of(in, out);

  public ByteLimiter(long maximumBytes) {
    this.maximumBytes = maximumBytes;
  }

  @Override
  public FlowShape<ByteString, ByteString> shape() {
    return shape;
  }

  @Override
  public GraphStageLogic createLogic(Attributes inheritedAttributes) {
    return new GraphStageLogic(shape) {
      private int count = 0;

      {
        setHandler(
            out,
            new AbstractOutHandler() {
              @Override
              public void onPull() throws Exception {
                pull(in);
              }
            });
        setHandler(
            in,
            new AbstractInHandler() {
              @Override
              public void onPush() throws Exception {
                ByteString chunk = grab(in);
                count += chunk.size();
                if (count > maximumBytes) {
                  failStage(new MaxSizeExceeded("Too much bytes"));
                } else {
                  push(out, chunk);
                }
              }
            });
      }
    };
  }

  public static class MaxSizeExceeded extends IllegalStateException {
    public MaxSizeExceeded(String message) {
      super(message);
    }
  }
}
