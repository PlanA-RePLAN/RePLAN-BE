CREATE UNIQUE INDEX uq_todo_routine_duedate
    ON todo (routine_id, due_date);
