import asyncio
from collections.abc import Awaitable, Callable

import firebase_admin
from firebase_admin import auth


TokenVerifier = Callable[[str], Awaitable[dict]]


class FirebaseTokenVerifier:
    def __init__(self, project_id: str):
        self.project_id = project_id

    async def __call__(self, token: str) -> dict:
        try:
            firebase_admin.get_app()
        except ValueError:
            firebase_admin.initialize_app(options={"projectId": self.project_id})
        return await asyncio.to_thread(auth.verify_id_token, token, True)
