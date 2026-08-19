package cn.gekal.spring.approval.infrastructure.config;

import cn.gekal.spring.approval.domain.model.ApproverRole;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 認証・認可設定。
 *
 * <p>サンプルを単体で動かせるようにするため、HTTP Basic + インメモリユーザーで構成している。ユーザーに付与する権限文字列は BPMN の {@code
 * candidateGroups}（managers / directors）と一致させ、そのまま候補グループとして使う。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /** 申請者グループ。承認タスクの候補にはならない。 */
  public static final String GROUP_APPLICANTS = "applicants";

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests.requestMatchers("/actuator/**").permitAll().anyRequest().authenticated())
        .httpBasic(Customizer.withDefaults())
        .build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
    UserDetails applicant =
        User.withUsername("yamada")
            .password(passwordEncoder.encode("password"))
            .authorities(GROUP_APPLICANTS)
            .build();
    UserDetails manager =
        User.withUsername("sato")
            .password(passwordEncoder.encode("password"))
            .authorities(GROUP_APPLICANTS, ApproverRole.MANAGER.groupId())
            .build();
    UserDetails director =
        User.withUsername("tanaka")
            .password(passwordEncoder.encode("password"))
            .authorities(GROUP_APPLICANTS, ApproverRole.DIRECTOR.groupId())
            .build();
    return new InMemoryUserDetailsManager(applicant, manager, director);
  }
}
