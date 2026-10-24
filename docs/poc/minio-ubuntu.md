# Chạy và xác minh MinIO trên Ubuntu

Trạng thái: mã Java đã có unit test; MinIO thật, Docker image và persistence sau restart server chưa được xác minh trên máy hiện tại. Không dùng shared Supabase cho bất kỳ bước nào dưới đây.

## Chuẩn bị

Dùng Ubuntu 24.04 LTS, JDK 21, Docker Engine và Compose v2 có hỗ trợ up --wait. Cài Docker theo [hướng dẫn Ubuntu chính thức](https://docs.docker.com/engine/install/ubuntu/). Cần Internet khi build source image và tải Gradle dependencies. Kiểm tra java -version, docker version và docker compose version. Các lệnh dưới đây chạy bằng Bash từ repository root.

MinIO Community hiện phân phối source và repository đã archived. Dockerfile cố định server commit 9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a và mc commit 77f82e18b5401a65958f1619df6ebb994634bd88. Build image này trước; không thay bằng latest chưa kiểm thử. Image/base OS cần được scan và ghi nhận digest trước khi đưa vào production. Compose một node phục vụ demo/local deployment; HA và backup cần thiết kế vận hành riêng.

## Nhập cấu hình riêng tư

Không bật set -x, không in docker compose config hoặc docker inspect vào báo cáo vì chúng có thể chứa môi trường. Spring Boot không tự đọc .env. Các script cung cấp dùng môi trường process và vô hiệu hóa tự đọc .env của Compose bằng --env-file /dev/null.

~~~bash
read -r -p 'MinIO root user: ' MINIO_ROOT_USER
read -r -s -p 'MinIO root password: ' MINIO_ROOT_PASSWORD; printf '\n'
read -r -p 'Application access key (distinct from root): ' MINIO_STORAGE_ACCESS_KEY
read -r -s -p 'Application secret key: ' MINIO_STORAGE_SECRET_KEY; printf '\n'
export MINIO_ROOT_USER MINIO_ROOT_PASSWORD MINIO_STORAGE_ACCESS_KEY MINIO_STORAGE_SECRET_KEY
export MINIO_STORAGE_BUCKET=olp-objects
export MINIO_STORAGE_KEY_PREFIX=olp
export MINIO_STORAGE_ENDPOINT=http://127.0.0.1:9000
export MINIO_STORAGE_ALLOW_INSECURE_HTTP=true
export MINIO_STORAGE_REGION=us-east-1
export DXOS_OBJECT_STORAGE_PROVIDER=minio
export MINIO_STORAGE_STAGING_DIRECTORY="$HOME/.local/share/olp/minio-staging"
install -d -m 700 "$MINIO_STORAGE_STAGING_DIRECTORY"
~~~

Dùng credentials mới, đủ mạnh, được quản lý riêng cho môi trường này. Backend chỉ nhận MINIO_STORAGE_ACCESS_KEY/SECRET_KEY, không dùng root. Trong production, cấp credentials qua cơ chế quản lý secret/process và dùng endpoint HTTPS. HTTP chỉ được bật rõ ràng cho loopback hoặc mạng Docker phát triển được cô lập.

## Build và khởi động

~~~bash
bash infra/minio/start.sh
curl --fail --silent http://127.0.0.1:9000/minio/health/ready
~~~

Script build image từ source, chờ MinIO healthy rồi chạy bootstrap. Bootstrap tạo bucket private, cấm anonymous access và cấp GetObject/PutObject/DeleteObject trong prefix olp/v1/* cùng ListBucket có điều kiện prefix. Nó không cấp quyền quản trị, tạo bucket hoặc truy cập prefix khác cho ứng dụng. Identity có sẵn với policy/group không phù hợp sẽ làm bootstrap thất bại để tránh kế thừa quyền rộng.

API: 127.0.0.1:9000. Console: 127.0.0.1:9001. Truy cập từ xa có thể dùng SSH tunnel; không đổi bind thành public chỉ để demo. Dữ liệu nằm trong named volume olp-minio_minio-data; không dùng down -v. Khi backend ở container khác, endpoint phải là hostname service minio:9000 trên cùng Docker network, không phải localhost của container backend.

## Compile, unit test, build và integration thật

~~~bash
cd backend
bash ./gradlew compileJava compileTestJava
bash ./gradlew test build
cd ..
bash infra/minio/run-integration.sh
~~~

Regular test/build loại cả supabase-real và minio-real. Suite MinIO thật sử dụng server local đang chạy; không tự tạo Testcontainer hoặc tự kết nối dịch vụ từ xa. Script kiểm tra health rồi truyền credentials qua môi trường sang suite. Java tiếp tục kiểm tra endpoint loopback trước mọi thao tác. Gọi trực tiếp minioIntegrationTest mà thiếu MINIO_TEST_* sẽ fail, không âm thầm skip.

Script run-integration.sh ánh xạ MINIO_STORAGE_* sang MINIO_TEST_ENDPOINT/ACCESS_KEY/SECRET_KEY/BUCKET/KEY_PREFIX/STAGING_DIRECTORY. Integration chỉ xóa object UUID do chính lần test tạo. Dùng bucket/prefix demo riêng; lần chạy bị ngắt có thể để lại object test.

Gates cần đạt: store/read/metadata/delete, zero-byte, length mismatch giữ object cũ, full metadata replacement, Unicode/case/code-unit round-trip, hai JVM CREATE_ONLY có đúng một winner và một CONFLICT, concurrent OVERWRITE giữ đúng cặp bytes/metadata, open-read snapshot qua overwrite/delete, đóng partial stream, JVM mới đọc được dữ liệu, unsigned GET bị từ chối, credentials sai được sanitize, và app không ghi được ngoài prefix.

Reports: backend/build/reports/tests/test/ và backend/build/reports/tests/minioIntegrationTest/. Ghi ngày, OS/JDK, image digest/source commits, lệnh và số test/failure; không ghi credentials.

## Chứng minh volume tồn tại qua restart server

Integration fresh-JVM không chứng minh restart MinIO. Thực hiện thêm kiểm tra vận hành sau, bằng ứng dụng mc được cài từ cùng source image:

Dùng canary có UUID ngẫu nhiên dưới đúng prefix, application identity và SHA-256. Canary được giữ lại để vận hành kiểm chứng; script không xóa dữ liệu. Đây là kiểm tra trực tiếp volume bằng mc; fidelity của adapter được kiểm tra bởi integration suite.

~~~bash
export MINIO_DEMO_KEY="demo-$(openssl rand -hex 16)"
bash infra/minio/durability.sh create
docker compose --env-file /dev/null -f infra/minio/compose.yml restart minio
curl --retry 12 --retry-connrefused --retry-delay 2 --fail --silent http://127.0.0.1:9000/minio/health/ready
bash infra/minio/durability.sh verify
docker compose --env-file /dev/null -f infra/minio/compose.yml up -d --force-recreate --wait minio
bash infra/minio/durability.sh verify
~~~

Giữ nguyên MINIO_DEMO_KEY, Compose project và named volume. So sánh cùng SHA-256 trước/sau restart và recreate, ghi kết quả vào docs/evidence/minio-object-storage.md. Không coi chỉ restart JVM hoặc HTTP health là bằng chứng persistence. Canary dùng namespace demo riêng và không kiểm thử conditional PUT của adapter; không dùng script này như upload API nghiệp vụ.

## Chạy backend

Trong process backend, cung cấp các biến MINIO_STORAGE_* ở trên và DB_* của PostgreSQL riêng đã được cấp. Có thể bỏ root credentials khỏi môi trường process backend. Từ repository root, chạy (cd backend && bash ./gradlew bootRun) hoặc java -jar backend/build/libs/dxos-backend-0.0.1-SNAPSHOT.jar. Full Spring Boot startup vẫn cần PostgreSQL hiện có; không dùng shared Supabase để hoàn thành demo này. Không có upload HTTP endpoint mới; integration gọi ObjectStoragePort trực tiếp.

## Xử lý lỗi

- INVALID_INPUT lúc startup: kiểm tra provider, HTTPS/HTTP opt-in, bucket/prefix, đường dẫn tuyệt đối, limits và timeout; không in secret để debug.
- UNAVAILABLE khi staging: kiểm tra dung lượng, owner và mode 0700 của thư mục; temp file dùng 0600. Không xóa temp file của upload đang chạy.
- ACCESS_FAILURE: kiểm tra application identity, policy và prefix. Không cấp root cho backend để né lỗi quyền.
- NOT_FOUND khác bucket thiếu: adapter dùng scoped LIST để phân biệt; bucket cấu hình thiếu là UNAVAILABLE.
- Bootstrap lỗi: kiểm tra health và identity mới không có policy/group cũ. Script cố ý không in raw mc error hoặc secret.
- UNAVAILABLE sau mutation: kết quả có thể đã được publish; đọc/metadata để reconcile trước khi retry.
