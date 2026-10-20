# Demo DX-OS Portal tại OLP 2026

**Chỉ trình diễn MOCK / DEVELOPMENT. Không tuyên bố đã tích hợp Core qua HTTP.**

## Chuẩn bị

1. Chạy `npm.cmd ci` và `npm.cmd run dev` trong `frontend/`.
2. Mở `http://127.0.0.1:5173/session`. Có thể dùng viewport desktop 1440 px; mobile 390 px; tablet 834 px.
3. Chọn **Nhân viên — Ngọc Ánh**, bấm **Vào phiên demo**. Mọi dữ liệu/tên trong UI là fixtures demo.
4. Khi cần bắt đầu lại: Trạng thái tích hợp → **Khôi phục dữ liệu demo**. Reload cũng reset dữ liệu yêu cầu.

## Kịch bản chính: hai dịch vụ, một nền tảng

1. Dashboard: chỉ ra thống kê lấy từ fixtures hiển thị cho vai trò hiện tại, bảng yêu cầu và hoạt động gần đây.
2. Bấm **Tạo yêu cầu mới**, chọn **Hỗ trợ CNTT**. Gửi form trống để thấy validation và focus trường lỗi đầu tiên.
3. Nhập tiêu đề tối thiểu 8 ký tự, mô tả tối thiểu 20 ký tự, vị trí và ưu tiên. Attachments ghi **BLOCKED FEATURE**, không nhận tệp.
4. Gửi: nút có loading/chống gửi trùng, sau đó mở chi tiết và thông báo thành công. Ghi lại mã yêu cầu, thường `DX-1049` ở phiên mới.
5. Dùng **sidebar** mở Trạng thái phiên → chọn **Người xử lý — Lê Xuân Bảo** → Tiếp tục. Không reload trang trong luồng demo.
6. Mở Bàn xử lý hoặc Yêu cầu của tôi, chọn mã vừa tạo. Phê duyệt → nhập ghi chú → xác nhận. Tiếp tục Bắt đầu xử lý → Hoàn thành, mỗi bước thêm sự kiện timeline. Yêu cầu kết thúc không có thao tác tiếp.
7. Mở Lịch sử hoạt động để theo dõi các sự kiện mới nhất.
8. Đổi về Nhân viên và lặp lại với **Cơ sở vật chất**. Giải thích hai dịch vụ dùng chung form/model/service/timeline; phản hồi hiện tại là mock, Core mới sở hữu runtime thật.

## Các trạng thái cần trình diễn

- **Search/filter/empty:** mở danh sách, tìm `cap quyen`, kết hợp filter Cơ sở vật chất để nhận empty state. Xóa bộ lọc; dùng nút Trước/Sau.
- **Loading:** mở danh sách/chi tiết; mock có độ trễ 350 ms. Gửi form có trạng thái Đang gửi.
- **Lỗi mạng:** Trạng thái tích hợp → chọn Mất kết nối mô phỏng → Áp dụng → Mở danh sách yêu cầu. Bấm Tải lại dữ liệu để phục hồi; không tự retry.
- **401:** áp dụng lỗi Phiên hết hạn rồi mở danh sách. Phiên demo bị kết thúc và trang phiên hiển thị thông báo 401 mô phỏng.
- **403/404:** áp dụng lỗi tương ứng rồi mở danh sách. Không có dữ liệu được hiển thị trong lỗi. Nhân viên mở `/requests/DX-1047` nhận cùng thông báo với mã không tồn tại. Không gọi đây là test security thật.
- **Kết quả ghi chưa xác định:** áp dụng lỗi này, dùng sidebar về Tổng quan rồi mở form mới và gửi đầy đủ. Lỗi được giữ cho lần ghi tiếp theo dù có đọc dữ liệu; nút gửi bị vô hiệu hóa sau lỗi, không tự gửi lại. Kiểm tra danh sách/lịch sử theo hướng dẫn.
- **Từ chối:** chọn một yêu cầu chờ, dùng vai trò Người xử lý, nhập lý do và xác nhận. Timeline có lý do; yêu cầu kết thúc.
- **Bàn phím:** focus hiển thị rõ, form có labels, dialog giữ focus và Escape đóng; mobile menu hỗ trợ Escape. Reduced motion được tôn trọng.

## Kết thúc bằng Integration Status

Mở Trạng thái tích hợp và bấm **Kiểm tra chặn LIVE**. Nêu rõ:

- Portal Tầng 2 build/run độc lập; không sửa backend.
- `RequestService` là interface dùng chung; composition root đang gắn `MockRequestService`.
- `HttpApiClient` luôn từ chối trước mạng, chưa có nút hoặc `.env` bật LIVE.
- Identity/Gateway/Workflow chờ security gate #22/#23/#24; API contract chờ #37; attachments chờ API có kiểm quyền #26.
- Chỉ khi có HTTP end-to-end thật sau security review mới đánh giá DoD của Issue #39. **Giữ Issue OPEN.**

Các screenshot trong [evidence](../docs/evidence/issue39-portal/README.md) được tạo bằng browser automation trên localhost, không phải mockup hình ảnh.
