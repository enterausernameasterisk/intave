/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.cloud.protocol.pipeline;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.cloud.Session;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.ReadTimeoutException;

public final class Errors extends ChannelInboundHandlerAdapter {
  private final Session session;

  public Errors(Session session) {
    this.session = session;
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable throwable) {
    String phase = session.started() ? "active session" : "handshake";
    String failure;
    if (rootCause(throwable) instanceof ReadTimeoutException) {
      failure = "read timeout";
    } else {
      failure = Session.describeFailure(throwable);
    }
    IntaveLogger.logger().error(
      "[Cloud] Network pipeline failed during " + phase
        + " with " + context.channel().remoteAddress()
        + " (handlers: " + context.pipeline().names() + "): "
        + failure + ". Closing the connection"
    );
    throwable.printStackTrace();
    context.close().addListener(closeFuture -> {
      if (!closeFuture.isSuccess()) {
        IntaveLogger.logger().error(
          "[Cloud] Failed to close the broken cloud connection: "
            + Session.describeFailure(closeFuture.cause())
        );
      }
    });
  }

  private static Throwable rootCause(Throwable throwable) {
    Throwable root = throwable;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    return root;
  }
}
