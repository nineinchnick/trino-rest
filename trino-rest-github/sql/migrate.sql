-- Execute these statements to generate queries to migrate data from old schema to new one.
-- Replace `old_schema` and `new_schema` with valid schema names.
SELECT '-- Create `new_schema` and create tables by executing base.sql';
SELECT '-- Alter defaults for new columns in the statements below if needed';

WITH columns AS (
  SELECT table_schema AS schema, table_name AS table, listagg(column_name , ', ') WITHING GROUP (ORDER BY ordinal_position) AS column_names
  FROM old_schema.information_schema.columns
  GROUP BY 1, 2
  ORDER BY 1, 2
)
SELECT
  'INSERT INTO new_schema.' || table_name || ' (' || (SELECT c.column_names FROM columns c WHERE c.schema = table_schema AND c.table = table_name) || ') ' ||
  'SELECT * FROM old_schema.' || table_name || ';'
FROM old_schema.information_schema.tables
WHERE table_schema = 'default';
