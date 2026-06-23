-- [*추가3] xlsx 등 첨부 불가 수정: file_type(varchar 10)이 MIME 타입을 담기엔 짧아
-- xlsx MIME('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'=65자)·pdf 등
-- 업로드 시 "Data too long for column 'file_type'"(strict mode) 500 발생.
-- file_type을 varchar(100)으로 확장(전체 MIME 보관).
ALTER TABLE `files`
  MODIFY COLUMN `file_type` varchar(100) DEFAULT NULL;
