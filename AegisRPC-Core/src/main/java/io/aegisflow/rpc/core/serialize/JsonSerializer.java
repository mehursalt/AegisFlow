package io.aegisflow.rpc.core.serialize;

import io.aegisflow.rpc.core.enums.AlgorithmTypeEnum;
import com.alibaba.fastjson.JSON;

/**
 * JsonSerializer类
 */
public class JsonSerializer implements Serializer{
    @Override
    public <T> byte[] serialize(T obj) throws Exception {
        //使用fastjson进行序列化
        return JSON.toJSONString(obj).getBytes();
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) throws Exception {
        //return JSON.parseObject(bytes,clazz);
        return JSON.parseObject(new String(bytes),clazz);
    }

    @Override
    public byte getSerializerType() {
        return AlgorithmTypeEnum.JSON.getCode();
    }
}
