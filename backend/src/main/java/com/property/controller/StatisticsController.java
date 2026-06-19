package com.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    private final BuildingMapper buildingMapper;
    private final ReceivableMapper receivableMapper;
    private final SysConfigMapper configMapper;
    
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

    @GetMapping("/building-summary")
    public Result<BuildingSummaryResponse> getBuildingSummary(
            @RequestParam(required = false) String feeType) {

        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        BigDecimal arrearsThreshold = getArrearsThreshold();

        String currentPeriod = resolveCurrentPeriodMonth(accountSetId);

        LambdaQueryWrapper<Building> buildingWrapper = new LambdaQueryWrapper<>();
        buildingWrapper.eq(Building::getAccountSetId, accountSetId);
        buildingWrapper.orderByAsc(Building::getBuildingNo);
        List<Building> allBuildings = buildingMapper.selectList(buildingWrapper);

        LambdaQueryWrapper<Owner> ownerWrapper = new LambdaQueryWrapper<>();
        ownerWrapper.eq(Owner::getAccountSetId, accountSetId);
        List<Owner> allOwners = ownerMapper.selectList(ownerWrapper);
        Map<Long, Owner> ownerMap = allOwners.stream()
                .collect(Collectors.toMap(Owner::getId, o -> o));

        LambdaQueryWrapper<Receivable> recWrapper = new LambdaQueryWrapper<>();
        recWrapper.eq(Receivable::getAccountSetId, accountSetId)
                  .eq(Receivable::getPeriodMonth, currentPeriod);
        if (feeType != null && !feeType.isEmpty()) {
            recWrapper.eq(Receivable::getFeeType, feeType);
        }
        List<Receivable> receivables = receivableMapper.selectList(recWrapper);

        Map<Long, OwnerReceivableSummary> ownerSummaryMap = new HashMap<>();
        for (Receivable r : receivables) {
            OwnerReceivableSummary s = ownerSummaryMap.computeIfAbsent(
                    r.getOwnerId(), k -> new OwnerReceivableSummary());
            s.receivable = s.receivable.add(r.getAmount());
            s.received = s.received.add(r.getPaidAmount());
            s.feeItems.add(new FeeItem(r.getFeeType(), r.getAmount(), r.getPaidAmount()));
        }

        Map<String, List<Owner>> ownersByBuilding = allOwners.stream()
                .collect(Collectors.groupingBy(Owner::getBuildingNo));

        List<BuildingSummaryItem> buildingItems = new ArrayList<>();
        for (Building b : allBuildings) {
            String buildingNo = b.getBuildingNo();
            List<Owner> buildingOwners = ownersByBuilding.getOrDefault(buildingNo, Collections.emptyList());

            BigDecimal receivable = BigDecimal.ZERO;
            BigDecimal received = BigDecimal.ZERO;
            long arrearsOwnerCount = 0;

            Map<String, List<Owner>> ownersByUnit = buildingOwners.stream()
                    .collect(Collectors.groupingBy(Owner::getUnitNo));

            List<UnitSummaryItem> unitItems = new ArrayList<>();
            List<String> sortedUnitNos = new ArrayList<>(ownersByUnit.keySet());
            sortedUnitNos.sort(Comparator.naturalOrder());

            for (String unitNo : sortedUnitNos) {
                List<Owner> unitOwners = ownersByUnit.get(unitNo);

                BigDecimal unitReceivable = BigDecimal.ZERO;
                BigDecimal unitReceived = BigDecimal.ZERO;
                List<RoomDetailItem> roomItems = new ArrayList<>();

                for (Owner owner : unitOwners) {
                    OwnerReceivableSummary s = ownerSummaryMap.getOrDefault(owner.getId(), new OwnerReceivableSummary());
                    BigDecimal ownerArrears = s.receivable.subtract(s.received);

                    unitReceivable = unitReceivable.add(s.receivable);
                    unitReceived = unitReceived.add(s.received);

                    String feeDetail = s.feeItems.stream()
                            .map(f -> resolveFeeName(f.feeType) + ": 应收￥" + f.amount + " / 已收￥" + f.paid)
                            .collect(Collectors.joining("<br/>"));
                    if (feeDetail.isEmpty()) feeDetail = "无费用";

                    RoomDetailItem room = new RoomDetailItem();
                    room.setOwnerId(owner.getId());
                    room.setOwnerName(owner.getName());
                    room.setRoomNo(owner.getRoomNo());
                    room.setPhone(owner.getPhone());
                    room.setFeeName(feeDetail);
                    room.setReceivable(s.receivable);
                    room.setReceived(s.received);
                    room.setArrears(ownerArrears);
                    room.setHasArrears(ownerArrears.compareTo(BigDecimal.ZERO) > 0);
                    roomItems.add(room);
                }

                BigDecimal unitArrears = unitReceivable.subtract(unitReceived);
                BigDecimal unitCollectionRate = unitReceivable.compareTo(BigDecimal.ZERO) > 0
                        ? unitReceived.multiply(BigDecimal.valueOf(100)).divide(unitReceivable, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                UnitSummaryItem unit = new UnitSummaryItem();
                unit.setUnitNo(unitNo);
                unit.setReceivable(unitReceivable);
                unit.setReceived(unitReceived);
                unit.setArrears(unitArrears);
                unit.setCollectionRate(unitCollectionRate);
                unit.setRoomCount((long) unitOwners.size());
                unit.setRooms(roomItems);
                unitItems.add(unit);

                receivable = receivable.add(unitReceivable);
                received = received.add(unitReceived);
            }

            for (Owner owner : buildingOwners) {
                OwnerReceivableSummary s = ownerSummaryMap.get(owner.getId());
                if (s != null && s.receivable.subtract(s.received).compareTo(BigDecimal.ZERO) > 0) {
                    arrearsOwnerCount++;
                }
            }

            BigDecimal arrears = receivable.subtract(received);
            BigDecimal collectionRate = receivable.compareTo(BigDecimal.ZERO) > 0
                    ? received.multiply(BigDecimal.valueOf(100)).divide(receivable, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal arrearsRate = receivable.compareTo(BigDecimal.ZERO) > 0
                    ? arrears.multiply(BigDecimal.valueOf(100)).divide(receivable, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BuildingSummaryItem building = new BuildingSummaryItem();
            building.setBuildingNo(buildingNo);
            building.setReceivable(receivable);
            building.setReceived(received);
            building.setArrears(arrears);
            building.setCollectionRate(collectionRate);
            building.setArrearsRate(arrearsRate);
            building.setHighlighted(arrearsRate.compareTo(arrearsThreshold) > 0);
            building.setRoomCount((long) buildingOwners.size());
            building.setArrearsRoomCount(arrearsOwnerCount);
            building.setUnits(unitItems);
            buildingItems.add(building);
        }

        BuildingSummaryResponse response = new BuildingSummaryResponse();
        response.setThreshold(arrearsThreshold);
        response.setCurrentPeriod(currentPeriod);
        response.setBuildings(buildingItems);

        BigDecimal totalReceivable = BigDecimal.ZERO;
        BigDecimal totalReceived = BigDecimal.ZERO;
        long totalOwners = 0;
        long totalArrearsOwners = 0;
        for (BuildingSummaryItem b : buildingItems) {
            totalReceivable = totalReceivable.add(b.getReceivable());
            totalReceived = totalReceived.add(b.getReceived());
            totalOwners += b.getRoomCount();
            totalArrearsOwners += b.getArrearsRoomCount();
        }
        response.setTotalReceivable(totalReceivable);
        response.setTotalReceived(totalReceived);
        response.setTotalArrears(totalReceivable.subtract(totalReceived));
        response.setTotalOwners(totalOwners);
        response.setTotalArrearsOwners(totalArrearsOwners);
        response.setTotalCollectionRate(totalReceivable.compareTo(BigDecimal.ZERO) > 0
                ? totalReceived.multiply(BigDecimal.valueOf(100)).divide(totalReceivable, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        return Result.success(response);
    }

    private String resolveCurrentPeriodMonth(Long accountSetId) {
        LambdaQueryWrapper<Receivable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Receivable::getAccountSetId, accountSetId)
               .orderByDesc(Receivable::getPeriodMonth)
               .last("LIMIT 1");
        Receivable latest = receivableMapper.selectOne(wrapper);
        if (latest != null) {
            return latest.getPeriodMonth();
        }
        return LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private static class OwnerReceivableSummary {
        BigDecimal receivable = BigDecimal.ZERO;
        BigDecimal received = BigDecimal.ZERO;
        List<FeeItem> feeItems = new ArrayList<>();
    }

    @Data
    private static class FeeItem {
        String feeType;
        BigDecimal amount;
        BigDecimal paid;
        FeeItem(String feeType, BigDecimal amount, BigDecimal paid) {
            this.feeType = feeType;
            this.amount = amount;
            this.paid = paid;
        }
    }

    private String resolveFeeName(String feeType) {
        return switch (feeType) {
            case "PROPERTY" -> "物业费";
            case "PARKING" -> "车位费";
            case "WATER" -> "水费";
            case "ELECTRIC" -> "电费";
            case "GAS" -> "燃气费";
            case "HEATING" -> "暖气费";
            default -> feeType;
        };
    }

    private BigDecimal getArrearsThreshold() {
        String val = configMapper.getValueByKey("arrears_rate_threshold");
        if (val != null) {
            try {
                return new BigDecimal(val);
            } catch (NumberFormatException ignored) {}
        }
        return new BigDecimal("20");
    }

    @Data
    public static class BuildingSummaryResponse {
        private String currentPeriod;
        private BigDecimal threshold;
        private BigDecimal totalReceivable;
        private BigDecimal totalReceived;
        private BigDecimal totalArrears;
        private BigDecimal totalCollectionRate;
        private Long totalOwners;
        private Long totalArrearsOwners;
        private List<BuildingSummaryItem> buildings;
    }

    @Data
    public static class BuildingSummaryItem {
        private String buildingNo;
        private BigDecimal receivable;
        private BigDecimal received;
        private BigDecimal arrears;
        private BigDecimal collectionRate;
        private BigDecimal arrearsRate;
        private Boolean highlighted;
        private Long roomCount;
        private Long arrearsRoomCount;
        private List<UnitSummaryItem> units;
    }

    @Data
    public static class UnitSummaryItem {
        private String unitNo;
        private BigDecimal receivable;
        private BigDecimal received;
        private BigDecimal arrears;
        private BigDecimal collectionRate;
        private Long roomCount;
        private List<RoomDetailItem> rooms;
    }

    @Data
    public static class RoomDetailItem {
        private Long ownerId;
        private String ownerName;
        private String roomNo;
        private String phone;
        private String feeName;
        private BigDecimal receivable;
        private BigDecimal received;
        private BigDecimal arrears;
        private Boolean hasArrears;
    }
}
