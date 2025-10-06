package com.example.demo;

public class SQLSolver {

    /**
     * Returns the SQL query for:
     * Highest salary credited NOT on the 1st day of the month,
     * along with employee name, age, and department name.
     */
    public static String solveHighestSalaryNotOnFirstDay() {
        return """
                SELECT p.amount AS SALARY,
                       CONCAT(e.first_name, ' ', e.last_name) AS NAME,
                       TIMESTAMPDIFF(YEAR, e.dob, p.payment_time) AS AGE,
                       d.department_name AS DEPARTMENT_NAME
                FROM payments p
                JOIN employee e ON p.emp_id = e.emp_id
                JOIN department d ON e.department = d.department_id
                WHERE DAY(p.payment_time) <> 1
                  AND p.amount = (SELECT MAX(amount) FROM payments WHERE DAY(payment_time) <> 1);
                """;
    }
}
