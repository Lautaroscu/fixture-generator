package com.fixture.fixturesservice.services;

import com.fixture.fixturesservice.DTOS.FechaDTO;
import com.fixture.fixturesservice.DTOS.PartidoDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ExcelService {

    public byte[] generarExcelFixture(List<FechaDTO> fechas) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Fixture");

            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            int rowIdx = 0;

            for (FechaDTO fecha : fechas) {
                Row rowFecha = sheet.createRow(rowIdx++);
                Cell cellFecha = rowFecha.createCell(0);
                cellFecha.setCellValue("FECHA " + fecha.getNroFecha());
                cellFecha.setCellStyle(headerStyle);
                sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 2));
                Row headerRow = sheet.createRow(rowIdx++);
                String[] columns = {"Local", "Visitante", "Cancha"};
                for (int i = 0; i < columns.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(columns[i]);
                    cell.setCellStyle(headerStyle);
                }

                for (PartidoDTO partido : fecha.getPartidos()) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(partido.getLocal());
                    row.createCell(1).setCellValue(partido.getVisitante());
                    row.createCell(2).setCellValue(partido.getCancha());
                }
                rowIdx++;
            }

            for (int i = 0; i < 3; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }
}