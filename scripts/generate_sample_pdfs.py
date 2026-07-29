from __future__ import annotations

import io
from pathlib import Path

import fitz
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import (
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "tmp" / "pdfs" / "fixtures"
DIGITAL = OUTPUT / "verbatim-product-guide.pdf"
SCANNED = OUTPUT / "verbatim-product-guide-scanned.pdf"


def build_digital() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    styles = getSampleStyleSheet()
    title = ParagraphStyle(
        "Title",
        parent=styles["Title"],
        fontName="Helvetica-Bold",
        fontSize=30,
        leading=34,
        textColor=colors.HexColor("#173f33"),
        alignment=TA_LEFT,
        spaceAfter=12,
    )
    eyebrow = ParagraphStyle(
        "Eyebrow",
        parent=styles["Normal"],
        fontName="Helvetica-Bold",
        fontSize=9,
        leading=12,
        textColor=colors.HexColor("#68766f"),
        uppercase=True,
        spaceAfter=8,
    )
    body = ParagraphStyle(
        "Body",
        parent=styles["BodyText"],
        fontName="Helvetica",
        fontSize=11,
        leading=17,
        textColor=colors.HexColor("#27352e"),
        spaceAfter=10,
    )
    heading = ParagraphStyle(
        "Heading",
        parent=styles["Heading2"],
        fontName="Helvetica-Bold",
        fontSize=17,
        leading=21,
        textColor=colors.HexColor("#173f33"),
        spaceBefore=12,
        spaceAfter=8,
    )

    def decorate(canvas, document) -> None:
        width, height = A4
        canvas.saveState()
        canvas.setFillColor(colors.HexColor("#f5f2ea"))
        canvas.rect(0, 0, width, height, stroke=0, fill=1)
        canvas.setFillColor(colors.HexColor("#173f33"))
        canvas.roundRect(18 * mm, height - 25 * mm, 18 * mm, 10 * mm, 3 * mm, stroke=0, fill=1)
        canvas.setFillColor(colors.white)
        canvas.setFont("Helvetica-Bold", 8)
        canvas.drawCentredString(27 * mm, height - 21.2 * mm, "V")
        canvas.setFillColor(colors.HexColor("#68766f"))
        canvas.setFont("Helvetica", 8)
        canvas.drawString(18 * mm, 13 * mm, "VERBATIM  /  PRODUCT GUIDE")
        canvas.drawRightString(width - 18 * mm, 13 * mm, str(document.page))
        canvas.restoreState()

    story = [
        Spacer(1, 14 * mm),
        Paragraph("PROJECT-AWARE TRANSLATION", eyebrow),
        Paragraph("Welcome to LingoHub, %{username}.", title),
        Paragraph(
            "Open Settings to continue. This short guide explains how Verbatim keeps "
            "terminology, translation memory, and page layout together while a document "
            "moves through review.",
            body,
        ),
        Spacer(1, 4 * mm),
        Paragraph("A document keeps its shape", heading),
        Paragraph(
            "Every translated page retains its dimensions, visual hierarchy, images, and "
            "section rhythm. If a translation cannot fit above the configured minimum font "
            "scale, Verbatim flags the page instead of hiding the compromise.",
            body,
        ),
        Paragraph("Review stages", heading),
        Table(
            [
                ["Stage", "Purpose", "Result"],
                ["Preflight", "Read text and page geometry", "Ready to translate"],
                ["Linguistic QA", "Check terms and placeholders", "Passed or flagged"],
                ["Visual QA", "Compare rendered pages", "Ready or layout flagged"],
            ],
            colWidths=[34 * mm, 86 * mm, 45 * mm],
            repeatRows=1,
            style=TableStyle(
                [
                    ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#173f33")),
                    ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                    ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                    ("FONTNAME", (0, 1), (-1, -1), "Helvetica"),
                    ("FONTSIZE", (0, 0), (-1, -1), 9),
                    ("LEADING", (0, 0), (-1, -1), 13),
                    ("BACKGROUND", (0, 1), (-1, -1), colors.white),
                    ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#e9eee9")]),
                    ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#9ca8a1")),
                    ("VALIGN", (0, 0), (-1, -1), "TOP"),
                    ("LEFTPADDING", (0, 0), (-1, -1), 7),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 7),
                    ("TOPPADDING", (0, 0), (-1, -1), 7),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
                ]
            ),
        ),
        Spacer(1, 8 * mm),
        Paragraph("What drives a translation", heading),
        Paragraph(
            "Project rules remain visible and editable. Document instructions apply only to "
            "the current document, while terminology such as LingoHub or Settings can be "
            "promoted deliberately for future work.",
            body,
        ),
    ]

    document = SimpleDocTemplate(
        str(DIGITAL),
        pagesize=A4,
        leftMargin=18 * mm,
        rightMargin=18 * mm,
        topMargin=18 * mm,
        bottomMargin=22 * mm,
        title="Verbatim Product Guide",
        author="Verbatim",
    )
    document.build(story, onFirstPage=decorate, onLaterPages=decorate)


def build_scanned() -> None:
    source = fitz.open(DIGITAL)
    output = fitz.open()
    for page in source:
        pixmap = page.get_pixmap(matrix=fitz.Matrix(2.0, 2.0), alpha=False)
        image_bytes = pixmap.tobytes("png")
        target = output.new_page(width=page.rect.width, height=page.rect.height)
        target.insert_image(target.rect, stream=io.BytesIO(image_bytes).getvalue())
    output.save(SCANNED, deflate=True)
    output.close()
    source.close()


if __name__ == "__main__":
    build_digital()
    build_scanned()
    print(DIGITAL)
    print(SCANNED)
