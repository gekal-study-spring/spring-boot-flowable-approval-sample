package cn.gekal.spring.approval.infrastructure.config;

import cn.gekal.spring.approval.domain.model.ApproverRole;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, ObjectProvider<CorsConfigurationSource> corsConfigurationSource)
      throws Exception {
    // 開発用の CORS 設定が登録されているときだけ有効にする（既定では同一オリジンなので不要）
    CorsConfigurationSource source = corsConfigurationSource.getIfAvailable();
    if (source != null) {
      http.cors(cors -> cors.configurationSource(source));
    }
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests
                    // 認証が要るのは API だけ。動作確認用 GUI（Next.js の静的書き出し）と
                    // Actuator は認証なしで配信し、画面側のログインフォームから API を叩かせる
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .httpBasic(basic -> basic.authenticationEntryPoint(jsonAuthenticationEntryPoint()))
        .build();
  }

  /**
   * 認証失敗時のレスポンス。
   *
   * <p>既定の {@code BasicAuthenticationEntryPoint} は {@code WWW-Authenticate} を返すため、GUI から fetch した際に
   * ブラウザ標準の認証ダイアログが開いてしまう。ここでは JSON だけを返し、認証は画面側のログインフォームに任せる。
   */
  private AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
    return (request, response, authException) -> {
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding("UTF-8");
      response
          .getWriter()
          .write(
              """
              {"type":"https://example.com/errors/401","title":"認証が必要です","status":401,\
              "detail":"ユーザー名とパスワードを確認してください","instance":"%s","timestamp":"%s"}"""
                  .formatted(request.getRequestURI(), LocalDateTime.now()));
    };
  }

  /**
   * `next dev`（web/ の開発サーバ）から API を叩くための CORS 設定。
   *
   * <p>ビルドした GUI は Spring Boot が同一オリジンで配信するため通常は不要。開発サーバを使うときだけ
   * `--app.web-dev-origin=http://localhost:3000` を付けて起動する。
   */
  @Bean
  @ConditionalOnProperty(name = "app.web-dev-origin")
  public CorsConfigurationSource devCorsConfigurationSource(
      @org.springframework.beans.factory.annotation.Value("${app.web-dev-origin}") String origin) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(origin));
    configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
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
