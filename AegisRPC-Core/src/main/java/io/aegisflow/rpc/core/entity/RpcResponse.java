package io.aegisflow.rpc.core.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * RpcResponse类
 * 当前方法就是RPC的相应类信息
 */
@Data
public class RpcResponse implements Serializable {
    private Object data;
    private String msg;
}
