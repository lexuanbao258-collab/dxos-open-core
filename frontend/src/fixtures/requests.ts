import type {
  ServiceRequest,
  RequestKind,
  RequestStatus,
} from "../domain/request";

// Entirely synthetic university scenarios. NOT operational data or policies of Đại học CMC.
const rows: [
  string,
  string,
  RequestKind,
  RequestStatus,
  ServiceRequest["priority"],
  string,
  string,
][] = [
  ["1048", "Cấp quyền truy cập phòng máy thực hành", "it", "pending", "high", "Ngọc Ánh", "Khoa Công nghệ thông tin"],
  ["1047", "Điều hòa phòng học B203 không làm mát", "facility", "processing", "high", "Minh Anh", "Phòng Đào tạo"],
  ["1046", "Cài đặt phần mềm lập trình cho phòng máy", "it", "processing", "normal", "Ngọc Ánh", "Khoa Công nghệ thông tin"],
  ["1045", "Chuẩn bị hội trường cho hội thảo sinh viên", "facility", "approved", "normal", "Ngọc Ánh", "Khoa Công nghệ thông tin"],
  ["1044", "Không kết nối được Wi-Fi thư viện", "it", "completed", "urgent", "Hoàng Nam", "Thư viện"],
  ["1043", "Thay đèn phòng học A305", "facility", "pending", "normal", "Ngọc Ánh", "Khoa Công nghệ thông tin"],
  ["1042", "Thiết lập tài khoản học tập trực tuyến", "it", "completed", "normal", "Ngọc Ánh", "Khoa Công nghệ thông tin"],
  ["1041", "Bổ sung màn hình cho phòng thực hành", "facility", "rejected", "high", "Ngọc Ánh", "Khoa Công nghệ thông tin"],
  ["1040", "Kiểm tra máy in phòng khảo thí", "it", "pending", "normal", "Thu Hà", "Phòng Khảo thí"],
  ["1039", "Bảo trì bàn ghế phòng học C201", "facility", "completed", "normal", "Ngọc Ánh", "Khoa Công nghệ thông tin"],
];

export function makeFixtures(): ServiceRequest[] {
  return rows.map(
    ([number, title, kind, status, priority, requester, department], index) => {
      const id = `DX-${number}`;
      const createdAt = new Date(
        Date.UTC(2026, 9, 20 - Math.floor(index / 2), 2, 15 + index),
      ).toISOString();
      const timeline = [
        {
          id: `${id}-1`,
          at: createdAt,
          actor: requester,
          title: "Đã gửi yêu cầu",
          note: "Ghi nhận từ cổng dịch vụ trường học trong môi trường mock.",
        },
      ];
      if (status !== "pending")
        timeline.push({
          id: `${id}-2`,
          at: new Date(Date.parse(createdAt) + 3600000).toISOString(),
          actor: "Lê Xuân Bảo",
          title: status === "rejected" ? "Từ chối yêu cầu" : "Đã phê duyệt",
          note:
            status === "rejected"
              ? "Cần bổ sung phương án và dự toán trước khi trình duyệt lại."
              : "Đã kiểm tra thông tin và chuyển bộ phận phụ trách.",
        });
      if (status === "processing" || status === "completed")
        timeline.push({
          id: `${id}-3`,
          at: new Date(Date.parse(createdAt) + 7200000).toISOString(),
          actor: kind === "it" ? "Tổ hỗ trợ CNTT" : "Tổ cơ sở vật chất",
          title: "Bắt đầu xử lý",
          note: "Bộ phận phụ trách đã tiếp nhận theo quy trình mô phỏng.",
        });
      if (status === "completed")
        timeline.push({
          id: `${id}-4`,
          at: new Date(Date.parse(createdAt) + 10800000).toISOString(),
          actor: kind === "it" ? "Tổ hỗ trợ CNTT" : "Tổ cơ sở vật chất",
          title: "Hoàn thành yêu cầu",
          note: "Đã xử lý và xác nhận kết quả trong kịch bản mock.",
        });
      return {
        id,
        title,
        description:
          kind === "it"
            ? "Cần hỗ trợ thiết bị, phần mềm hoặc tài khoản để hoạt động học tập và giảng dạy diễn ra thuận lợi. Đề nghị bộ phận CNTT kiểm tra và hướng dẫn."
            : "Đề nghị bộ phận cơ sở vật chất kiểm tra thiết bị hoặc không gian phục vụ học tập và giảng dạy, ưu tiên không làm gián đoạn giờ học.",
        kind,
        status,
        priority,
        requester,
        department,
        location:
          kind === "it" ? "Tòa A · Khu phòng máy" : "Tòa B · Khu giảng đường",
        assignee:
          status === "pending"
            ? "Chưa phân công"
            : kind === "it"
              ? "Tổ hỗ trợ CNTT"
              : "Tổ cơ sở vật chất",
        createdAt,
        updatedAt: timeline.at(-1)!.at,
        version: timeline.length - 1,
        timeline,
      };
    },
  );
}
