package com.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.common.BusinessException;
import com.property.common.ErrorCode;
import com.property.common.Result;
import com.property.config.AccountSetContext;
import com.property.entity.*;
import com.property.mapper.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/statistics")
@RequiredArgsConstructor
public class StatisticsController {
    
    private final BillMapper billMapper;
    private final PaymentMapper paymentMapper;
    private final OwnerMapper ownerMapper;
    private final ParkingMapper parkingMapper;
    private final SysConfigMapper sysConfigMapper;
    private final TemplateMapper templateMapper;
    
    @GetMapping("/dashboard")
    public Result<DashboardData> getDashboard() {
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        DashboardData data = new DashboardData();
        
        LambdaQueryWrapper<Owner> ownerWrapper = new LambdaQueryWrapper<>();
        ownerWrapper.eq(Owner::getAccountSetId, accountSetId);
        data.setTotalOwners(ownerMapper.selectCount(ownerWrapper));
        
        ownerWrapper.eq(Owner::getStatus, "OCCUPIED");
        data.setOccupiedOwners(ownerMapper.selectCount(ownerWrapper));
        
        LambdaQueryWrapper<Parking> parkingWrapper = new LambdaQueryWrapper<>();
        parkingWrapper.eq(Parking::getAccountSetId, accountSetId);
        data.setTotalParkings(parkingMapper.selectCount(parkingWrapper));
        
        parkingWrapper.eq(Parking::getStatus, "USED");
        data.setUsedParkings(parkingMapper.selectCount(parkingWrapper));
        
        LambdaQueryWrapper<Bill> billWrapper = new LambdaQueryWrapper<>();
        billWrapper.eq(Bill::getAccountSetId, accountSetId);
        List<Bill> bills = billMapper.selectList(billWrapper);
        
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal paidAmount = BigDecimal.ZERO;
        long overdueCount = 0;
        
        for (Bill bill : bills) {
            totalAmount = totalAmount.add(bill.getAmount());
            paidAmount = paidAmount.add(bill.getPaidAmount());
            if ("OVERDUE".equals(bill.getStatus())) {
                overdueCount++;
            }
        }
        
        data.setTotalAmount(totalAmount);
        data.setPaidAmount(paidAmount);
        data.setUnpaidAmount(totalAmount.subtract(paidAmount));
        data.setOverdueCount(overdueCount);
        
        if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            data.setCollectionRate(paidAmount.multiply(BigDecimal.valueOf(100))
                    .divide(totalAmount, 2, RoundingMode.HALF_UP));
            data.setArrearsRate(data.getUnpaidAmount().multiply(BigDecimal.valueOf(100))
                    .divide(totalAmount, 2, RoundingMode.HALF_UP));
        } else {
            data.setCollectionRate(BigDecimal.ZERO);
            data.setArrearsRate(BigDecimal.ZERO);
        }
        
        // 计算账龄（逾期天数）
        LocalDate today = LocalDate.now();
        int totalOverdueDays = 0;
        int maxOverdueDays = 0;
        int overdueCountForAge = 0;
        for (Bill bill : bills) {
            if ("OVERDUE".equals(bill.getStatus()) && bill.getDueDate() != null) {
                int days = (int) java.time.temporal.ChronoUnit.DAYS.between(bill.getDueDate(), today);
                if (days > 0) {
                    totalOverdueDays += days;
                    maxOverdueDays = Math.max(maxOverdueDays, days);
                    overdueCountForAge++;
                }
            }
        }
        data.setAvgOverdueDays(overdueCountForAge > 0 ? totalOverdueDays / overdueCountForAge : 0);
        data.setMaxOverdueDays(maxOverdueDays);
        
        return Result.success(data);
    }
    
    @GetMapping("/payment-detail")
    public Result<List<PaymentDetail>> getPaymentDetail(
            @RequestParam String feeType,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        List<PaymentDetail> details = new ArrayList<>();
        
        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Payment::getAccountSetId, accountSetId)
               .ge(Payment::getCreateTime, startDate.atStartOfDay())
               .le(Payment::getCreateTime, endDate.plusDays(1).atStartOfDay());
        
        List<Payment> payments = paymentMapper.selectList(wrapper);
        
        for (Payment payment : payments) {
            Bill bill = billMapper.selectById(payment.getBillId());
            if (bill != null && feeType.equals(bill.getFeeType())) {
                Owner owner = ownerMapper.selectById(payment.getOwnerId());
                
                PaymentDetail detail = new PaymentDetail();
                detail.setPaymentNo(payment.getPaymentNo());
                detail.setOwnerName(owner != null ? owner.getName() : "");
                detail.setRoomInfo(owner != null ? owner.getBuildingNo() + "-" + owner.getUnitNo() + "-" + owner.getRoomNo() : "");
                detail.setFeeName(bill.getFeeName());
                detail.setAmount(payment.getActualAmount());
                detail.setPaymentMethod(payment.getPaymentMethod());
                detail.setPaymentTime(payment.getCreateTime().toString());
                details.add(detail);
            }
        }
        
        return Result.success(details);
    }
    
    @GetMapping("/arrears-detail")
    public Result<List<ArrearsDetail>> getArrearsDetail(
            @RequestParam String feeType,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        List<ArrearsDetail> details = new ArrayList<>();
        
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Bill::getAccountSetId, accountSetId)
               .eq(Bill::getFeeType, feeType)
               .in(Bill::getStatus, "UNPAID", "OVERDUE")
               .ge(Bill::getPeriodStart, startDate)
               .le(Bill::getPeriodEnd, endDate);
        
        List<Bill> bills = billMapper.selectList(wrapper);
        
        for (Bill bill : bills) {
            Owner owner = ownerMapper.selectById(bill.getOwnerId());
            
            ArrearsDetail detail = new ArrearsDetail();
            detail.setBillNo(bill.getBillNo());
            detail.setOwnerName(owner != null ? owner.getName() : "");
            detail.setRoomInfo(owner != null ? owner.getBuildingNo() + "-" + owner.getUnitNo() + "-" + owner.getRoomNo() : "");
            detail.setFeeName(bill.getFeeName());
            detail.setAmount(bill.getAmount().subtract(bill.getPaidAmount()));
            detail.setPeriod(bill.getPeriodStart() + " ~ " + bill.getPeriodEnd());
            detail.setDueDate(bill.getDueDate().toString());
            detail.setStatus(bill.getStatus());
            details.add(detail);
        }
        
        return Result.success(details);
    }
    
    @Data
    public static class DashboardData {
        private Long totalOwners;
        private Long occupiedOwners;
        private Long totalParkings;
        private Long usedParkings;
        private BigDecimal totalAmount;
        private BigDecimal paidAmount;
        private BigDecimal unpaidAmount;
        private Long overdueCount;
        private BigDecimal collectionRate;
        private BigDecimal arrearsRate;
        private Integer avgOverdueDays;
        private Integer maxOverdueDays;
    }
    
    @Data
    public static class PaymentDetail {
        private String paymentNo;
        private String ownerName;
        private String roomInfo;
        private String feeName;
        private BigDecimal amount;
        private String paymentMethod;
        private String paymentTime;
    }
    
    @Data
    public static class ArrearsDetail {
        private String billNo;
        private String ownerName;
        private String roomInfo;
        private String feeName;
        private BigDecimal amount;
        private String period;
        private String dueDate;
        private String status;
    }
    
    @GetMapping("/summary")
    public Result<SummaryData> getSummary(
            @RequestParam String feeType,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        SummaryData summary = new SummaryData();
        
        // 缴费汇总
        LambdaQueryWrapper<Payment> paymentWrapper = new LambdaQueryWrapper<>();
        paymentWrapper.eq(Payment::getAccountSetId, accountSetId)
                      .ge(Payment::getCreateTime, startDate.atStartOfDay())
                      .le(Payment::getCreateTime, endDate.plusDays(1).atStartOfDay());
        List<Payment> payments = paymentMapper.selectList(paymentWrapper);
        
        BigDecimal totalPaid = BigDecimal.ZERO;
        int paidCount = 0;
        for (Payment payment : payments) {
            Bill bill = billMapper.selectById(payment.getBillId());
            if (bill != null && feeType.equals(bill.getFeeType())) {
                totalPaid = totalPaid.add(payment.getActualAmount());
                paidCount++;
            }
        }
        summary.setTotalPaidAmount(totalPaid);
        summary.setPaidCount(paidCount);
        
        // 欠费汇总
        LambdaQueryWrapper<Bill> billWrapper = new LambdaQueryWrapper<>();
        billWrapper.eq(Bill::getAccountSetId, accountSetId)
                   .eq(Bill::getFeeType, feeType)
                   .in(Bill::getStatus, "UNPAID", "OVERDUE")
                   .ge(Bill::getPeriodStart, startDate)
                   .le(Bill::getPeriodEnd, endDate);
        List<Bill> bills = billMapper.selectList(billWrapper);
        
        BigDecimal totalArrears = BigDecimal.ZERO;
        for (Bill bill : bills) {
            totalArrears = totalArrears.add(bill.getAmount().subtract(bill.getPaidAmount()));
        }
        summary.setTotalArrearsAmount(totalArrears);
        summary.setArrearsCount(bills.size());
        
        return Result.success(summary);
    }
    
    @GetMapping("/payment-detail/export")
    public void exportPaymentDetail(
            @RequestParam String feeType,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            HttpServletResponse response) throws IOException {
        
        Result<List<PaymentDetail>> result = getPaymentDetail(feeType, startDate, endDate);
        List<PaymentDetail> details = result.getData();
        
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("缴费明细");
            String[] headers = {"缴费单号", "业主", "房间", "费用", "金额", "方式", "时间"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            
            int rowNum = 1;
            for (PaymentDetail detail : details) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(detail.getPaymentNo());
                row.createCell(1).setCellValue(detail.getOwnerName());
                row.createCell(2).setCellValue(detail.getRoomInfo());
                row.createCell(3).setCellValue(detail.getFeeName());
                row.createCell(4).setCellValue(detail.getAmount().doubleValue());
                row.createCell(5).setCellValue(detail.getPaymentMethod());
                row.createCell(6).setCellValue(detail.getPaymentTime());
            }
            
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + 
                URLEncoder.encode("缴费明细.xlsx", StandardCharsets.UTF_8));
            workbook.write(response.getOutputStream());
        }
    }
    
    @GetMapping("/arrears-detail/export")
    public void exportArrearsDetail(
            @RequestParam String feeType,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            HttpServletResponse response) throws IOException {
        
        Result<List<ArrearsDetail>> result = getArrearsDetail(feeType, startDate, endDate);
        List<ArrearsDetail> details = result.getData();
        
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("欠费明细");
            String[] headers = {"账单编号", "业主", "房间", "费用", "欠费金额", "周期", "截止日期"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            
            int rowNum = 1;
            for (ArrearsDetail detail : details) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(detail.getBillNo());
                row.createCell(1).setCellValue(detail.getOwnerName());
                row.createCell(2).setCellValue(detail.getRoomInfo());
                row.createCell(3).setCellValue(detail.getFeeName());
                row.createCell(4).setCellValue(detail.getAmount().doubleValue());
                row.createCell(5).setCellValue(detail.getPeriod());
                row.createCell(6).setCellValue(detail.getDueDate());
            }
            
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + 
                URLEncoder.encode("欠费明细.xlsx", StandardCharsets.UTF_8));
            workbook.write(response.getOutputStream());
        }
    }
    
    @Data
    public static class SummaryData {
        private BigDecimal totalPaidAmount;
        private Integer paidCount;
        private BigDecimal totalArrearsAmount;
        private Integer arrearsCount;
    }

    private String getConfigValue(String key, String defaultValue) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getConfigKey, key);
        SysConfig config = sysConfigMapper.selectOne(wrapper);
        return config != null && config.getConfigValue() != null ? config.getConfigValue() : defaultValue;
    }

    private LocalDate[] parsePeriodMonth(String periodMonth) {
        YearMonth ym;
        if (periodMonth == null || periodMonth.isEmpty()) {
            ym = YearMonth.now();
        } else {
            ym = YearMonth.parse(periodMonth, DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        return new LocalDate[]{start, end};
    }

    @GetMapping("/building-summary")
    public Result<BuildingSummaryResponse> getBuildingSummary(
            @RequestParam(required = false) String feeType,
            @RequestParam(required = false) String periodMonth) {
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();

        String thresholdStr = getConfigValue("arrears_threshold", "20");
        BigDecimal arrearsThreshold = new BigDecimal(thresholdStr);

        LocalDate[] period = parsePeriodMonth(periodMonth);
        LocalDate periodStart = period[0];
        LocalDate periodEnd = period[1];

        LambdaQueryWrapper<Owner> ownerWrapper = new LambdaQueryWrapper<>();
        ownerWrapper.eq(Owner::getAccountSetId, accountSetId);
        List<Owner> allOwners = ownerMapper.selectList(ownerWrapper);

        LambdaQueryWrapper<Bill> billWrapper = new LambdaQueryWrapper<>();
        billWrapper.eq(Bill::getAccountSetId, accountSetId)
                   .ge(Bill::getPeriodStart, periodStart)
                   .le(Bill::getPeriodEnd, periodEnd);
        if (feeType != null && !feeType.isEmpty()) {
            billWrapper.eq(Bill::getFeeType, feeType);
        }
        List<Bill> allBills = billMapper.selectList(billWrapper);

        Map<Long, List<Bill>> billsByOwner = allBills.stream()
                .collect(Collectors.groupingBy(Bill::getOwnerId));

        Map<String, List<Owner>> ownersByBuilding = allOwners.stream()
                .collect(Collectors.groupingBy(Owner::getBuildingNo));

        List<BuildingSummaryDTO> result = new ArrayList<>();

        for (Map.Entry<String, List<Owner>> entry : ownersByBuilding.entrySet()) {
            String buildingNo = entry.getKey();
            List<Owner> buildingOwners = entry.getValue();

            BigDecimal receivableAmount = BigDecimal.ZERO;
            BigDecimal paidAmount = BigDecimal.ZERO;
            long arrearsOwnerCount = 0;

            for (Owner owner : buildingOwners) {
                List<Bill> ownerBills = billsByOwner.getOrDefault(owner.getId(), Collections.emptyList());
                BigDecimal ownerArrears = BigDecimal.ZERO;
                for (Bill bill : ownerBills) {
                    receivableAmount = receivableAmount.add(bill.getAmount());
                    paidAmount = paidAmount.add(bill.getPaidAmount());
                    ownerArrears = ownerArrears.add(bill.getAmount().subtract(bill.getPaidAmount()));
                }
                if (ownerArrears.compareTo(BigDecimal.ZERO) > 0) {
                    arrearsOwnerCount++;
                }
            }

            BigDecimal arrearsAmount = receivableAmount.subtract(paidAmount);
            BigDecimal collectionRate = BigDecimal.ZERO;
            BigDecimal arrearsRate = BigDecimal.ZERO;

            if (receivableAmount.compareTo(BigDecimal.ZERO) > 0) {
                collectionRate = paidAmount.multiply(BigDecimal.valueOf(100))
                        .divide(receivableAmount, 2, RoundingMode.HALF_UP);
                arrearsRate = arrearsAmount.multiply(BigDecimal.valueOf(100))
                        .divide(receivableAmount, 2, RoundingMode.HALF_UP);
            }

            BuildingSummaryDTO dto = new BuildingSummaryDTO();
            dto.setBuildingNo(buildingNo);
            dto.setOwnerCount((long) buildingOwners.size());
            dto.setArrearsOwnerCount(arrearsOwnerCount);
            dto.setReceivableAmount(receivableAmount);
            dto.setPaidAmount(paidAmount);
            dto.setArrearsAmount(arrearsAmount);
            dto.setCollectionRate(collectionRate);
            dto.setArrearsRate(arrearsRate);
            dto.setHighlight(arrearsRate.compareTo(arrearsThreshold) >= 0);
            result.add(dto);
        }

        result.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a.getBuildingNo()), Integer.parseInt(b.getBuildingNo()));
            } catch (NumberFormatException e) {
                return a.getBuildingNo().compareTo(b.getBuildingNo());
            }
        });

        BuildingSummaryResponse response = new BuildingSummaryResponse();
        response.setPeriodMonth(periodStart.format(DateTimeFormatter.ofPattern("yyyy-MM")));
        response.setArrearsThreshold(arrearsThreshold);
        response.setBuildings(result);
        return Result.success(response);
    }

    @GetMapping("/building-detail")
    public Result<BuildingDetailResponse> getBuildingDetail(
            @RequestParam String buildingNo,
            @RequestParam(required = false) String feeType,
            @RequestParam(required = false) String periodMonth) {
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();

        LocalDate[] period = parsePeriodMonth(periodMonth);
        LocalDate periodStart = period[0];
        LocalDate periodEnd = period[1];

        LambdaQueryWrapper<Owner> ownerWrapper = new LambdaQueryWrapper<>();
        ownerWrapper.eq(Owner::getAccountSetId, accountSetId)
                .eq(Owner::getBuildingNo, buildingNo);
        List<Owner> buildingOwners = ownerMapper.selectList(ownerWrapper);

        LambdaQueryWrapper<Bill> billWrapper = new LambdaQueryWrapper<>();
        billWrapper.eq(Bill::getAccountSetId, accountSetId)
                   .ge(Bill::getPeriodStart, periodStart)
                   .le(Bill::getPeriodEnd, periodEnd);
        if (feeType != null && !feeType.isEmpty()) {
            billWrapper.eq(Bill::getFeeType, feeType);
        }
        List<Bill> allBills = billMapper.selectList(billWrapper);

        Map<Long, List<Bill>> billsByOwner = allBills.stream()
                .collect(Collectors.groupingBy(Bill::getOwnerId));

        Map<String, List<Owner>> ownersByUnit = buildingOwners.stream()
                .collect(Collectors.groupingBy(Owner::getUnitNo));

        List<UnitSummaryDTO> result = new ArrayList<>();

        for (Map.Entry<String, List<Owner>> entry : ownersByUnit.entrySet()) {
            String unitNo = entry.getKey();
            List<Owner> unitOwners = entry.getValue();

            UnitSummaryDTO unitDto = new UnitSummaryDTO();
            unitDto.setUnitNo(unitNo);

            List<RoomArrearsDTO> roomList = new ArrayList<>();
            BigDecimal unitReceivable = BigDecimal.ZERO;
            BigDecimal unitPaid = BigDecimal.ZERO;

            for (Owner owner : unitOwners) {
                List<Bill> ownerBills = billsByOwner.getOrDefault(owner.getId(), Collections.emptyList());

                BigDecimal ownerReceivable = BigDecimal.ZERO;
                BigDecimal ownerPaid = BigDecimal.ZERO;

                for (Bill bill : ownerBills) {
                    ownerReceivable = ownerReceivable.add(bill.getAmount());
                    ownerPaid = ownerPaid.add(bill.getPaidAmount());
                }

                BigDecimal ownerArrears = ownerReceivable.subtract(ownerPaid);
                unitReceivable = unitReceivable.add(ownerReceivable);
                unitPaid = unitPaid.add(ownerPaid);

                RoomArrearsDTO roomDto = new RoomArrearsDTO();
                roomDto.setOwnerId(owner.getId());
                roomDto.setOwnerName(owner.getName());
                roomDto.setRoomNo(owner.getRoomNo());
                roomDto.setPhone(owner.getPhone());
                roomDto.setReceivableAmount(ownerReceivable);
                roomDto.setPaidAmount(ownerPaid);
                roomDto.setArrearsAmount(ownerArrears);
                roomDto.setHasArrears(ownerArrears.compareTo(BigDecimal.ZERO) > 0);
                roomList.add(roomDto);
            }

            roomList.sort((a, b) -> {
                try {
                    return Integer.compare(Integer.parseInt(a.getRoomNo()), Integer.parseInt(b.getRoomNo()));
                } catch (NumberFormatException e) {
                    return a.getRoomNo().compareTo(b.getRoomNo());
                }
            });

            unitDto.setReceivableAmount(unitReceivable);
            unitDto.setPaidAmount(unitPaid);
            unitDto.setArrearsAmount(unitReceivable.subtract(unitPaid));
            unitDto.setRooms(roomList);
            result.add(unitDto);
        }

        result.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a.getUnitNo().replaceAll("[^0-9]", "")),
                        Integer.parseInt(b.getUnitNo().replaceAll("[^0-9]", "")));
            } catch (NumberFormatException e) {
                return a.getUnitNo().compareTo(b.getUnitNo());
            }
        });

        BuildingDetailResponse response = new BuildingDetailResponse();
        response.setPeriodMonth(periodStart.format(DateTimeFormatter.ofPattern("yyyy-MM")));
        response.setUnits(result);
        return Result.success(response);
    }

    @GetMapping("/owners-arrears")
    public Result<List<OwnerArrearsDTO>> getOwnersWithArrears(
            @RequestParam(required = false) String buildingNo,
            @RequestParam(required = false) String feeType,
            @RequestParam(required = false) String periodMonth) {
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();

        LocalDate[] period = parsePeriodMonth(periodMonth);
        LocalDate periodStart = period[0];
        LocalDate periodEnd = period[1];

        LambdaQueryWrapper<Owner> ownerWrapper = new LambdaQueryWrapper<>();
        ownerWrapper.eq(Owner::getAccountSetId, accountSetId);
        if (buildingNo != null && !buildingNo.isEmpty()) {
            ownerWrapper.eq(Owner::getBuildingNo, buildingNo);
        }
        List<Owner> owners = ownerMapper.selectList(ownerWrapper);

        LambdaQueryWrapper<Bill> billWrapper = new LambdaQueryWrapper<>();
        billWrapper.eq(Bill::getAccountSetId, accountSetId)
                   .ge(Bill::getPeriodStart, periodStart)
                   .le(Bill::getPeriodEnd, periodEnd)
                   .in(Bill::getStatus, "UNPAID", "OVERDUE");
        if (feeType != null && !feeType.isEmpty()) {
            billWrapper.eq(Bill::getFeeType, feeType);
        }
        List<Bill> bills = billMapper.selectList(billWrapper);

        Map<Long, List<Bill>> billsByOwner = bills.stream()
                .collect(Collectors.groupingBy(Bill::getOwnerId));

        List<OwnerArrearsDTO> result = new ArrayList<>();
        for (Owner owner : owners) {
            List<Bill> ownerBills = billsByOwner.getOrDefault(owner.getId(), Collections.emptyList());
            BigDecimal totalArrears = BigDecimal.ZERO;
            for (Bill bill : ownerBills) {
                totalArrears = totalArrears.add(bill.getAmount().subtract(bill.getPaidAmount()));
            }
            if (totalArrears.compareTo(BigDecimal.ZERO) > 0) {
                OwnerArrearsDTO dto = new OwnerArrearsDTO();
                dto.setOwnerId(owner.getId());
                dto.setOwnerName(owner.getName());
                dto.setBuildingNo(owner.getBuildingNo());
                dto.setUnitNo(owner.getUnitNo());
                dto.setRoomNo(owner.getRoomNo());
                dto.setPhone(owner.getPhone());
                dto.setCumulativeArrears(totalArrears);
                dto.setBillCount(ownerBills.size());
                result.add(dto);
            }
        }

        return Result.success(result);
    }

    @Data
    public static class BuildingSummaryDTO {
        private String buildingNo;
        private Long ownerCount;
        private Long arrearsOwnerCount;
        private BigDecimal receivableAmount;
        private BigDecimal paidAmount;
        private BigDecimal arrearsAmount;
        private BigDecimal collectionRate;
        private BigDecimal arrearsRate;
        private Boolean highlight;
    }

    @Data
    public static class UnitSummaryDTO {
        private String unitNo;
        private BigDecimal receivableAmount;
        private BigDecimal paidAmount;
        private BigDecimal arrearsAmount;
        private List<RoomArrearsDTO> rooms;
    }

    @Data
    public static class RoomArrearsDTO {
        private Long ownerId;
        private String ownerName;
        private String roomNo;
        private String phone;
        private BigDecimal receivableAmount;
        private BigDecimal paidAmount;
        private BigDecimal arrearsAmount;
        private Boolean hasArrears;
    }

    @Data
    public static class OwnerArrearsDTO {
        private Long ownerId;
        private String ownerName;
        private String buildingNo;
        private String unitNo;
        private String roomNo;
        private String phone;
        private BigDecimal cumulativeArrears;
        private Integer billCount;
    }

    @Data
    public static class BuildingSummaryResponse {
        private String periodMonth;
        private BigDecimal arrearsThreshold;
        private List<BuildingSummaryDTO> buildings;
    }

    @Data
    public static class BuildingDetailResponse {
        private String periodMonth;
        private List<UnitSummaryDTO> units;
    }
}
