import type {
  ServiceRequest,
  RequestKind,
  RequestStatus,
} from "../domain/request";

const rows: [
  string,
  string,
  RequestKind,
  RequestStatus,
  ServiceRequest["priority"],
  string,
  string,
][] = [
  [
    "1048",
    "Cấp quyền truy cập kho mã nguồn dự án",
    "it",
    "pending",
    "high",
    "Ngọc Ánh",
    "Phòng phát triển",
  ],
  [
    "1047",
    "Điều hòa phòng họp Lotus không làm mát",
    "facility",
    "processing",
    "high",
    "Minh Anh",
    "Phòng vận hành",
  ],
  [
    "1046",
    "Cài đặt môi trường phát triển cho laptop mới",
    "it",
    "processing",
    "normal",
    "Ngọc Ánh",
    "Phòng phát triển",
  ],
  [
    "1045",
    "Chuẩn bị phòng họp cho buổi demo OLP",
    "facility",
    "approved",
    "normal",
    "Ngọc Ánh",
    "Phòng phát triển",
  ],
  [
    "1044",
    "Không kết nối được mạng Wi-Fi nội bộ",
    "it",
    "completed",
    "urgent",
    "Hoàng Nam",
    "Phòng kinh doanh",
  ],
  [
    "1043",
    "Thay đèn khu vực làm việc tầng 3",
    "facility",
    "pending",
    "normal",
    "Ngọc Ánh",
    "Phòng phát triển",
  ],
  [
    "1042",
    "Thiết lập tài khoản công cụ thiết kế",
    "it",
    "completed",
    "normal",
    "Ngọc Ánh",
    "Phòng phát triển",
  ],
  [
    "1041",
    "Bổ sung màn hình cho khu vực cộng tác",
    "facility",
    "rejected",
    "high",
    "Ngọc Ánh",
    "Phòng phát triển",
  ],
  [
    "1040",
    "Kiểm tra máy in phòng hành chính",
    "it",
    "pending",
    "normal",
    "Thu Hà",
    "Phòng hành chính",
  ],
  [
    "1039",
    "Bảo trì bàn làm việc khu B",
    "facility",
    "completed",
    "normal",
    "Ngọc Ánh",
    "Phòng phát triển",
  ],
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
          note: "Yêu cầu được tiếp nhận trong môi trường demo.",
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
              ? "Cần bổ sung kế hoạch ngân sách trước khi gửi lại."
              : "Đã xác nhận thông tin, chuyển đến nhóm phụ trách.",
        });
      if (status === "processing" || status === "completed")
        timeline.push({
          id: `${id}-3`,
          at: new Date(Date.parse(createdAt) + 7200000).toISOString(),
          actor: kind === "it" ? "Nhóm IT Support" : "Nhóm Facility",
          title: "Bắt đầu xử lý",
          note: "Nhóm phụ trách đã tiếp nhận công việc.",
        });
      if (status === "completed")
        timeline.push({
          id: `${id}-4`,
          at: new Date(Date.parse(createdAt) + 10800000).toISOString(),
          actor: kind === "it" ? "Nhóm IT Support" : "Nhóm Facility",
          title: "Hoàn thành yêu cầu",
          note: "Đã xử lý và xác nhận kết quả trong kịch bản mock.",
        });
      return {
        id,
        title,
        description:
          kind === "it"
            ? "Cần hỗ trợ để công việc của nhóm được tiếp tục thuận lợi. Vui lòng kiểm tra cấu hình, xác nhận phạm vi truy cập và hướng dẫn các bước tiếp theo."
            : "Đề nghị nhóm vận hành kiểm tra và hỗ trợ khu vực được ghi nhận. Vui lòng phối hợp thời gian xử lý để không ảnh hưởng đến công việc của nhóm.",
        kind,
        status,
        priority,
        requester,
        department,
        location:
          kind === "it" ? "Tầng 3 · Khu phát triển" : "Tầng 2 · Phòng Lotus",
        assignee:
          status === "pending"
            ? "Chưa phân công"
            : kind === "it"
              ? "Nhóm IT Support"
              : "Nhóm Facility",
        createdAt,
        updatedAt: timeline.at(-1)!.at,
        version: timeline.length - 1,
        timeline,
      };
    },
  );
}
