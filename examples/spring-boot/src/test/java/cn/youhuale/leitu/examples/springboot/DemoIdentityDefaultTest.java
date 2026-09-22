package cn.youhuale.leitu.examples.springboot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 反向自测（#18）：金样本默认的失败方向必须是安全侧。
 *
 * <p>DemoContextFilter 里"从请求头取身份"只为让演示跑通，默认（{@code demo.trust-request-headers=false}）
 * 一律匿名。而匿名不参与角色判定——于是照抄这份代码的人，在没接认证之前拿到的是 403，
 * 而不是"发个 X-Demo-User: admin 就当上管理员"。这两种失败形态的差别就是这次要守的东西。
 */
@SpringBootTest(properties = "demo.trust-request-headers=false")
@AutoConfigureMockMvc
class DemoIdentityDefaultTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 默认不信任请求头_自称alice也判匿名_403() throws Exception {
        mvc.perform(post("/orders").param("id", "o-x").param("amountCents", "1")
                        .header("X-Demo-User", "alice").header("X-Demo-Tenant", "tenant-a"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("guard.denied"))
                .andExpect(jsonPath("$.detail").value(containsString("匿名")));
    }

    @Test
    void 未认证也不写库_判定在存取之前() throws Exception {
        mvc.perform(post("/orders").param("id", "o-y").param("amountCents", "1")
                        .header("X-Demo-User", "alice").header("X-Demo-Tenant", "tenant-a"))
                .andExpect(status().isForbidden());
    }
}
