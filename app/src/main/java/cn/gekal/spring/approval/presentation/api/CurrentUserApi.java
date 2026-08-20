package cn.gekal.spring.approval.presentation.api;

import cn.gekal.spring.approval.infrastructure.config.SecurityConfig;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ログイン中のユーザーを返す API。
 *
 * <p>画面が「誰として繋がっていて、何ができるか」を知るための入口。権限の判断をクライアント側の決め打ちにせず、サーバの認可設定と同じ情報を返す。
 */
@RestController
@RequestMapping("/api/me")
public class CurrentUserApi {

  /**
   * Spring Security が認証要素として自動で付ける権限の接頭辞。
   *
   * <p>{@code FACTOR_PASSWORD} のようにログイン手段を表すもので、業務上の権限ではない。画面にそのまま出すと candidateGroups
   * と混ざって紛らわしいため落とす。
   */
  private static final String AUTHENTICATION_FACTOR_PREFIX = "FACTOR_";

  @GetMapping
  public CurrentUserResponse me(Authentication authentication) {
    List<String> authorities =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> !authority.startsWith(AUTHENTICATION_FACTOR_PREFIX))
            .sorted()
            .toList();
    return new CurrentUserResponse(
        authentication.getName(),
        authorities,
        authorities.contains(SecurityConfig.GROUP_ADMINISTRATORS));
  }
}
