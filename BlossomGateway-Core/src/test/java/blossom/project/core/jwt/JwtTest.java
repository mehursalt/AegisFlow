package blossom.project.core.jwt;

import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.Test;

import java.util.Date;

/**
 * JwtTest类
 */
public class JwtTest {
    @Test
    public void jwt() {
        // 定义一个密钥用于签名 JWT
        byte[] security = new byte[32];
        new java.security.SecureRandom().nextBytes(security);

        // 创建一个 JWT Token
        String token = Jwts.builder()
                .setSubject("1001") // Synthetic test identity.
                .setIssuedAt(new Date()) // 设置JWT的签发时间，通常为当前时间
                .signWith(SignatureAlgorithm.HS256, security) // 使用HS256算法签名JWT，使用密钥"security"
                .compact(); // 构建JWT并返回字符串表示

        // 打印生成的JWT Token

        // 解析JWT Token
        Jwt jwt = Jwts.parser().setSigningKey(security).parse(token);

        // 打印解析后的JWT对象

        // 从JWT中获取主题（Subject）信息
        String subject = ((DefaultClaims) jwt.getBody()).getSubject();
        org.junit.Assert.assertEquals("1001", subject);

        // 打印JWT中的主题信息
    }

}
