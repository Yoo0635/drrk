# 로그인 인증 구현 요약

## 1. 사용한 방식

이 프로젝트는 다음을 조합해 인증한다.

- **Access Token**: HS256 JWT, 30분
- **Refresh Token**: 32바이트 난수 토큰, 14일
- **저장 위치**: 두 토큰 모두 `HttpOnly Cookie`
- **세션 저장소**: Redis
- **CSRF 방어**: `csrf_token` 쿠키와 `X-CSRF-Token` 헤더 비교

JWT만 확인하는 방식이 아니다. 요청마다 JWT를 검증한 뒤 Redis 세션도 확인하므로 로그아웃이나 계정 정지를 즉시 반영할 수 있다.

## 2. 쿠키 구성

| 쿠키 | 설정 | 용도 |
| --- | --- | --- |
| `access_token` | `HttpOnly`, `Secure`, `Path=/` | API 인증 |
| `refresh_token` | `HttpOnly`, `Secure`, `Path=/api/v1/auth` | Access Token 재발급 |
| `csrf_token` | JavaScript 접근 가능, `Secure`, `Path=/` | CSRF 검증 |

운영 환경에서는 HTTPS와 `Secure=true`를 반드시 사용한다.

## 3. 로그인 흐름

```text
1. 이메일과 비밀번호를 받는다.
2. BCrypt로 비밀번호를 검증한다.
3. sessionId, refreshToken, csrfToken을 난수로 생성한다.
4. Access Token JWT를 발급한다.
5. Refresh Token은 SHA-256 해시만 Redis에 저장한다.
6. 세 토큰을 쿠키로 응답한다.
```

JWT에는 다음 정보만 넣는다.

```json
{
  "sub": "사용자 ID",
  "sid": "Redis 세션 ID",
  "email": "user@example.com",
  "role": "user",
  "iat": 0,
  "exp": 0
}
```

비밀번호가 틀린 경우와 존재하지 않는 이메일은 모두 `401 INVALID_CREDENTIALS`로 응답한다.

## 4. Redis 구조

```text
auth:session:{sessionId}
  userId
  email
  role
  status
  refreshTokenHash
  prevRefreshTokenHash

auth:refresh:{refreshTokenHash} -> sessionId
auth:user:{userId}:sessions     -> sessionId 목록
```

Redis에는 Refresh Token 원문을 저장하지 않는다.

## 5. 요청 인증

보호 API 요청이 오면 다음 순서로 확인한다.

```text
1. access_token 쿠키를 읽는다.
2. JWT 서명과 만료 시간을 검증한다.
3. JWT의 sid로 Redis 세션을 조회한다.
4. JWT와 Redis의 userId, email, role이 같은지 확인한다.
5. Redis 세션 상태가 active인지 확인한다.
6. 통과하면 Spring Security Authentication을 생성한다.
```

Controller에서는 다음처럼 사용한다.

```java
public ResponseEntity<?> getMe(
    @AuthenticationPrincipal AuthenticatedUser user
) {
    return ResponseEntity.ok(userService.getMe(user));
}
```

관리자 API는 서버에서 `hasRole("admin")`으로 검사한다. 프론트가 보내는 role이나 userId는 권한 판단에 사용하지 않는다.

## 6. Refresh Token 갱신

```text
1. refresh_token 쿠키를 해시한다.
2. Redis에서 세션을 찾는다.
3. 새 Access Token, Refresh Token, CSRF Token을 만든다.
4. 기존 Refresh Token 해시를 prevRefreshTokenHash로 옮긴다.
5. 새 Refresh Token 해시를 저장한다.
6. 세 쿠키를 새 값으로 교체한다.
```

직전 Refresh Token이 다시 사용되면 `401 REFRESH_TOKEN_REUSED`를 반환하고 해당 사용자의 모든 세션을 폐기한다.

실서비스에서는 Refresh Token 확인과 회전을 Redis Lua script나 transaction으로 원자적으로 처리하는 것이 좋다.

## 7. CSRF 처리

프론트는 상태 변경 요청에 `csrf_token` 쿠키 값을 헤더로 복사한다.

```ts
apiClient.interceptors.request.use((config) => {
  if (config.method?.toLowerCase() !== "get") {
    const token = readCookie("csrf_token")
    if (token) config.headers.set("X-CSRF-Token", token)
  }
  return config
})
```

백엔드는 쿠키와 헤더가 같을 때만 `POST`, `PUT`, `PATCH`, `DELETE` 요청을 허용한다.

로그인과 회원가입은 아직 CSRF 쿠키가 없으므로 예외로 두고, refresh와 logout은 CSRF 검사를 적용한다.

## 8. 프론트 자동 갱신

Axios는 쿠키를 보내도록 설정한다.

```ts
const apiClient = axios.create({
  withCredentials: true,
})
```

보호 API가 `401`을 반환하면 다음과 같이 처리한다.

```text
1. POST /api/v1/auth/refresh 호출
2. 성공하면 실패했던 요청을 한 번만 재시도
3. refresh도 401/403이면 로그아웃 상태 처리
```

동시에 여러 요청이 401이어도 refresh 요청은 하나만 보내도록 공용 `refreshPromise`를 사용한다. 로그인, 소셜 로그인, refresh 요청 자체는 자동 재시도 대상에서 제외한다.

로그인 여부는 브라우저 저장소가 아니라 `GET /api/v1/users/me` 응답으로 판단한다.

## 9. 로그아웃

```text
POST /api/v1/auth/logout
```

서버는 Redis 세션을 삭제하고 세 쿠키를 `Max-Age=0`으로 만료시킨다. 프론트는 사용자별 Query Cache와 Mutation Cache도 함께 정리해야 한다.

## 10. 소셜 로그인

Google과 Kakao는 사용자 신원 확인에만 사용한다. 검증이 끝나면 이메일 로그인과 같은 자체 세션을 발급한다.

- Google: ID Token 서명, issuer, audience, nonce 검증
- Kakao: OAuth state 검증 → code 교환 → ID Token 검증
- 신규 사용자: 짧은 만료시간의 가입용 임시 토큰 발급
- 기존 사용자: 바로 Access/Refresh/CSRF 쿠키 발급

## 11. 필요한 설정

```dotenv
JWT_SECRET=<32바이트 이상의 무작위 값>
JWT_ACCESS_TOKEN_TTL_MINUTES=30
JWT_REFRESH_TOKEN_TTL_DAYS=14

REDIS_HOST=localhost
REDIS_PORT=6379

COOKIE_SECURE=true
COOKIE_SAME_SITE=Lax
COOKIE_DOMAIN=
CORS_ALLOWED_ORIGINS=https://frontend.example.com
```

프론트와 API가 다른 origin이면 CORS에서 정확한 origin을 지정하고 credentials를 허용한다. `*`와 credential cookie를 함께 사용하면 안 된다.

## 12. 구현 순서

1. 사용자 모델과 BCrypt 로그인 구현
2. Redis 세션 저장소 구현
3. JWT 발급기와 검증 필터 구현
4. 로그인·refresh·logout API 구현
5. 쿠키와 CSRF 필터 구현
6. 프론트 Axios interceptor 구현
7. `/users/me` 기반 로그인 상태 구현
8. 필요하면 Google/Kakao 로그인 추가

## 13. 필수 테스트

- 유효한 JWT라도 Redis 세션이 없으면 `401`
- 만료되거나 변조된 JWT는 `401`
- CSRF 쿠키와 헤더가 다르면 `403`
- Refresh Token 갱신 시 세 토큰이 모두 교체됨
- 직전 Refresh Token 재사용 시 전체 세션 폐기
- 동시에 여러 `401`이 발생해도 refresh 요청은 한 번만 실행
- 로그아웃 후 Redis 세션과 프론트 사용자 캐시가 제거됨
- 일반 사용자의 관리자 API 접근은 `403`

## 참고 코드

- 백엔드 인증: `ieum_BE/app-main/src/main/java/shinhan/fibri/ieum/main/auth/`
- Security 설정: `ieum_BE/app-main/src/main/java/shinhan/fibri/ieum/config/SecurityConfig.java`
- 프론트 API client: `ieum_FE/src/lib/api/client.ts`
- 자동 refresh: `ieum_FE/src/lib/api/session-interceptor.ts`
- 로그인 상태: `ieum_FE/src/features/session/`
