package com.waylo.media.storage;

import java.io.InputStream;

/** Потік обʼєкта зі сховища разом із тим, що треба віддати у відповіді. */
public record StoredObject(InputStream stream, String contentType, long size) {}
