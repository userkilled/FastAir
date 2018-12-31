package com.mob.lee.fastair.io

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.InetSocketAddress
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel


/**
 * Created by Andy on 2017/11/29.
 */
class SocketService(val scope : CoroutineScope, var keepAlive : Boolean = false) {
    /**
     * 读取监听器，消息到来会通知
     * @see read
     */
    private val readers = HashSet<Reader>()
    /**
     *发送消息
     * @see write
     */
    private val writers = Channel<Writer>(Channel.UNLIMITED)
    /**
     * 指示当前连接是否已经开启
     */
    private var isOpen : Boolean = false
    /**
     * 通信通道
     */
    private var channel : SocketChannel? = null

    private var server : ServerSocketChannel? = null
    /**
     * 建立连接，host为空作为客户端，否则作为服务器
     * @param port 端口号
     * @param host 主机地址，当该参数为null时，则开启一个socket监听
     **/
    fun open(port : Int, host : String? = null) = scope.launch(Dispatchers.IO) {
        if (isOpen) {
            return@launch
        }
        isOpen = true
        try {
            if (null != host) {
                var time=0
                //等待10S
                while (!(channel?.isConnected?:false)&&time<20) {
                    try {
                        channel = SocketChannel.open()
                        channel?.socket()?.reuseAddress = true
                        channel?.connect(InetSocketAddress(host, port))
                    } catch (e : ConnectException) {
                        delay(500)
                        time++
                        continue
                    }
                }
            } else {
                server = ServerSocketChannel.open()
                server?.socket()?.reuseAddress = true
                server?.socket()?.bind(InetSocketAddress(port))
                channel = server?.accept()
            }
            handleRead()
            handleWrite()
        } catch (e : Exception) {
            e.printStackTrace()
            channel?.let {
                it.close()
            }
            close(e)
        }
    }

    /**
     * 添加收到数据的监听
     **/
    fun read(reader : Reader) {
        readers.add(reader)
    }

    /**
     * 添加写入数据的监听
     */
    fun write(writer : Writer) = scope.launch {
        writers.send(writer)
    }

    /**
     * 关闭连接
     */
    fun close(e : Exception? = null) {
        if (! isOpen) {
            return
        }
        isOpen = false
        writers.close()
        for (r in readers) {
            r.onError(e?.message)
        }
        try {
            server?.close()
            channel?.close()
        } catch (e : Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 读取数据
     *
     * 1、读取固定的5个字节，里面包含4个字节的内容长度，和1个字节的类型标识
     * 2、分解类型和长度
     * 3、读取真正内容
     * 4、组装成消息对象，通知每个监听
     *
     * @see ProtocolType
     * @see ProtocolByte
     */
    private fun handleRead() = scope.launch(Dispatchers.IO) {
        while (isOpen || keepAlive) {
            try {
                val head = readFix(5)
                val type = head.get()
                val length = head.int
                val bytes = readFix(length)
                for (reader in readers) {
                    reader(ProtocolByte.wrap(bytes.array(), ProtocolType.wrap(type)))
                }
            } catch (e : Exception) {
                e.printStackTrace()
                for (reader in readers) {
                    reader?.onError(e.message)
                }
                //读取失败
                if (e is BufferUnderflowException || e is AsynchronousCloseException) {
                    close(e)
                    break
                }
            }
        }
    }

    /**
     * 发送数据
     */
    private fun handleWrite() = scope.launch(Dispatchers.Default) {
        while (isOpen || keepAlive) {
            try {
                val data = writers.receive()
                for (d in data) {
                    val bytes = d.bytes
                    while (bytes.hasRemaining()) {
                        channel?.write(bytes)
                    }
                }
            } catch (e : Exception) {
                e.printStackTrace()
                if (e is ClosedReceiveChannelException) {
                    close(e)
                    break
                }
            }
        }
    }

    /**
     * 读取特定长度的字节，可能会阻塞
     */
    private fun readFix(targetSize : Int) : ByteBuffer {
        val buffer = ByteBuffer.allocate(targetSize)
        while (buffer.hasRemaining() && isOpen) {
            channel?.read(buffer)
        }
        buffer.flip()
        return buffer
    }
}