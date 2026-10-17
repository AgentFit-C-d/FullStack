"""Stateless AI v2 calculation core.

This package deliberately has no database, authentication, HTTP, or user ownership
dependencies. The FastAPI layer supplies transient input and returns structured data.
"""

from .analysis import AnalysisCore

__all__ = ["AnalysisCore"]
