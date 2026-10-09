package blossom.project.rpc.core.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RPC header fields.
 * MessageHeader类
 * Rpc的请求头类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RpcHeader implements Serializable {
    //public static final long serialVersionUID = 200201141215L;
    //当前RPC服务版本号（借鉴Dubbo）
    private byte versionId;

    //使用的序列化/压缩算法
    private byte algorithmType;

    //请求类型
    private byte reqType;

    //请求ID
    private long reqId;

    //消息内容长度
    private int length;

}
