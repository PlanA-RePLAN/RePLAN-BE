package plana.replan.domain.auth.apple;

/**
 * 애플 토큰 엔드포인트 교환 응답. refreshToken은 탈퇴 시 철회용, sub는 신분증(identityToken)과 같은 사용자에게서 온 코드인지 확인(bind)하는 데
 * 쓴다.
 */
public record AppleTokenResponse(String refreshToken, String sub) {}
