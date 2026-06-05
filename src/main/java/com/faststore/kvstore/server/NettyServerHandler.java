package com.faststore.kvstore.server;

import com.faststore.kvstore.engine.OffHeapEngine;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class NettyServerHandler extends ChannelInboundHandlerAdapter {

    private final OffHeapEngine offHeapEngine;
    private final AdminController adminController;

    public NettyServerHandler(OffHeapEngine offHeapEngine, AdminController adminController) {
        this.offHeapEngine = offHeapEngine;
        this.adminController = adminController;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        long start = System.nanoTime();
        ByteBuf in = (ByteBuf) msg;
        String request = in.toString(CharsetUtil.UTF_8).trim();
        
        // Simple text protocol:
        // PUT:key:value
        // GET:key
        
        String response = "ERR\n";
        try {
            if (request.startsWith("PUT:")) {
                String[] parts = request.split(":", 3);
                if (parts.length == 3) {
                    offHeapEngine.put(parts[1], parts[2]);
                    response = "OK\n";
                }
            } else if (request.startsWith("GET:")) {
                String[] parts = request.split(":", 2);
                if (parts.length == 2) {
                    String value = offHeapEngine.get(parts[1]);
                    response = (value != null ? value : "NOT_FOUND") + "\n";
                }
            }
        } catch (Exception e) {
            response = "ERR:" + e.getMessage() + "\n";
        }
        
        ctx.write(Unpooled.copiedBuffer(response, CharsetUtil.UTF_8));
        adminController.recordRequest(System.nanoTime() - start);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
