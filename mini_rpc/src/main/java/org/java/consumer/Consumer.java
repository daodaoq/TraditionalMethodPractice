package org.java.consumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.java.api.Add;
import org.java.codec.RequestEncoder;
import org.java.exception.RpcException;
import org.java.message.Request;
import org.java.message.Response;
import org.java.codec.ResponseEncoder;
import org.java.codec.SSDecoder;
import org.java.register.ServiceMetadata;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 客户端
 */
@Slf4j
public class Consumer implements Add {

    // 在途请求
    private Map<Integer, CompletableFuture<Response>> inFlightRequestTable = new ConcurrentHashMap<>();

    // 连接管理器
    private ConnectionManager manager = new ConnectionManager(createBootstrap());

    private Bootstrap createBootstrap() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(new NioEventLoopGroup(4))
            .channel(NioSocketChannel.class)
            .handler(new ChannelInitializer<NioSocketChannel>() {
                @Override
                protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                    nioSocketChannel.pipeline()
                        .addLast(new SSDecoder())
                        .addLast(new RequestEncoder())
                        .addLast(new SimpleChannelInboundHandler<Response>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext channelHandlerContext,
                                                        Response response) throws Exception {
                                CompletableFuture<Response> responseFuture = inFlightRequestTable.remove(response.getRequestId());
                                if (responseFuture == null) {
                                    log.warn("request Id{}找不到", response.getRequestId());
                                    return;
                                }
                                responseFuture.complete(response);
                            }
                        });
                }
            });

        return bootstrap;
    }

    public Integer add(int a, int b) {
        try {
            CompletableFuture<Response> responseFuture = new CompletableFuture<>();
            Channel channel = manager.getChannel("localhost", 8888);
            if (channel == null) {
                throw new RpcException("provider 连接失败");
            }
            Request request = new Request();
            request.setMethodName("add");
            request.setParams(new Object[]{a, b});
            request.setParamsClass(new Class[]{int.class, int.class});
            request.setServiceName(Add.class.getName());
            channel.writeAndFlush(request).addListener(f -> {
                if (f.isSuccess()) {
                    inFlightRequestTable.put(request.getRequestId(), responseFuture);
                }
            });
            Response response = responseFuture.get(3, TimeUnit.SECONDS);
            if (response.getCode() == 200) {
                return (Integer) response.getResult();
            } else {
                throw new RpcException(response.getErrorMessage());
            }
        } catch (RpcException rpcException) {
            throw rpcException;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
