package fit.iuh.cnm_project_be.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestLoginController {

    @GetMapping("/user")
    public String test(){
        return "Đăng nhập với role user";
    }

    @GetMapping("/admin")
    public String test2(){
        return "Đăng nhập với role admin";
    }
}
