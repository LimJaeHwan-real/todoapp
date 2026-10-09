package com.example.todoapp.user.application.controller;

import com.example.todoapp.user.application.form.SignupForm;
import com.example.todoapp.user.domain.service.DuplicateUsernameException;
import com.example.todoapp.user.domain.service.UserRegistrationService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class AuthenticationController {

    private final UserRegistrationService registrationService;

    @InitBinder("signupForm")
    public void signupFields(WebDataBinder binder) {
        binder.setAllowedFields("username", "password");
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/signup")
    public String signup(Model model) {
        model.addAttribute("signupForm", new SignupForm());
        return "auth/signup";
    }

    @PostMapping("/signup")
    public String register(@ModelAttribute SignupForm signupForm, BindingResult bindingResult,
                           HttpServletResponse response) {
        try {
            if (bindingResult.hasErrors()) {
                return "auth/signup";
            }
            registrationService.register(signupForm.getUsername(), signupForm.getPassword());
            return "redirect:/login?registered";
        } catch (DuplicateUsernameException exception) {
            bindingResult.rejectValue("username", "username.duplicate", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            bindingResult.reject("signup.invalid", exception.getMessage());
        } catch (DataAccessException | TransactionException exception) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            bindingResult.reject("signup.unavailable", "지금은 회원가입을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        } finally {
            signupForm.setPassword(null);
        }
        return "auth/signup";
    }
}
