package plana.replan.domain.todo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import plana.replan.domain.todo.entity.Todo;

public interface TodoRepository extends JpaRepository<Todo, Long> {}
