# JPA 규칙

## DB 함수 작성 규칙

이 프로젝트는 **PostgreSQL**을 사용한다. JPQL에서 DB 함수 호출 시 반드시 PostgreSQL 호환 문법을 사용한다.

### 금지
```java
// MySQL 방언 — PostgreSQL에서 동작 안 함
FUNCTION('YEAR', g.dueDate)
FUNCTION('MONTH', g.dueDate)
FUNCTION('DAY', g.dueDate)
```

### 허용 (표준 SQL)
```java
// PostgreSQL 포함 대부분 DB에서 동작
EXTRACT(YEAR FROM g.dueDate)
EXTRACT(MONTH FROM g.dueDate)
EXTRACT(DAY FROM g.dueDate)
```

## @Query 작성 위치

복잡한 조건(메서드명으로 표현 불가)은 Repository 인터페이스에 `@Query`로 작성한다.

```java
@Query("SELECT g FROM Goal g WHERE g.user = :user AND EXTRACT(YEAR FROM g.dueDate) = :year ORDER BY g.id DESC")
List<Goal> findByUserAndYear(@Param("user") User user, @Param("year") int year, Pageable pageable);
```
