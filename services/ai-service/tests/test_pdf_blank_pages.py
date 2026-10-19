import base64
from datetime import datetime, timedelta, timezone
from io import BytesIO
from pathlib import Path
import unittest

from pypdf import PdfReader, PdfWriter

from app.ai_v2.analysis import AnalysisCore


def deadline():
    return (datetime.now(timezone.utc) + timedelta(seconds=10)).isoformat()


def pdf_with_blank_cover():
    encoded = (Path(__file__).parent / "fixtures" / "ai_v2" / "analysis.pdf.b64").read_text().strip()
    writer = PdfWriter()
    writer.add_blank_page(width=612, height=792)
    writer.add_page(PdfReader(BytesIO(base64.b64decode(encoded))).pages[0])
    output = BytesIO()
    writer.write(output)
    return output.getvalue()


class PdfBlankPageTests(unittest.TestCase):
    def test_blank_cover_does_not_reject_later_text(self):
        result = AnalysisCore().analyze("blank-cover", "pdf", pdf_with_blank_cover(), deadline())
        self.assertEqual(result["profile"]["domain"], "education")
        self.assertEqual(result["candidates"][0]["evidence"][0]["page"], 2)

    def test_all_blank_pages_still_rejected(self):
        writer = PdfWriter()
        writer.add_blank_page(width=612, height=792)
        output = BytesIO()
        writer.write(output)
        with self.assertRaisesRegex(ValueError, "NO_TEXT_LAYER"):
            AnalysisCore().analyze("blank-only", "pdf", output.getvalue(), deadline())


if __name__ == "__main__":
    unittest.main()
