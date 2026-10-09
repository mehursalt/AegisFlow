package blossom.project.rpc.core.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * RpcDto类就是RPC数据传输对象
 * 这个对象封装了当前项目完整的RPC网络通信传输时的数据
 * 包含完整请求头和传输的数据
 * 这个类的完整名称应该是：RPC data transfer complete object
 * 但是叫做RpcDTCO好丑啊哈哈哈
 */
@Data
public class RpcDto<T> implements Serializable {
    //请求头
    private RpcHeader header;

    //数据
    private T data;
}
