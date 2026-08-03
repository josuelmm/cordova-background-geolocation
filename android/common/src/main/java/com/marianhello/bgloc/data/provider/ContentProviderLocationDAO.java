package com.marianhello.bgloc.data.provider;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import com.marianhello.bgloc.ResourceResolver;
import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.data.LocationDAO;
import com.marianhello.bgloc.data.sqlite.SQLiteLocationContract;
import com.marianhello.bgloc.data.sqlite.SQLiteLocationContract.LocationEntry;
import com.marianhello.logging.LoggerManager;

import java.util.ArrayList;
import java.util.Collection;

import ru.andremoniy.sqlbuilder.SqlExpression;
import ru.andremoniy.sqlbuilder.SqlSelectStatement;

public class ContentProviderLocationDAO implements LocationDAO {
    private org.slf4j.Logger logger;
    private ContentResolver mResolver;
    private Uri mContentUri;
    private String mAuthority;

    public ContentProviderLocationDAO(Context context) {
        logger = LoggerManager.getLogger(ContentProviderLocationDAO.class);
        ResourceResolver resourceResolver = ResourceResolver.newInstance(context);
        mAuthority = resourceResolver.getAuthority();
        mContentUri = LocationContentProvider.getContentUri(mAuthority);
        mResolver = context.getApplicationContext().getContentResolver();
    }

    /**
     * Get locations that match whereClause
     *
     * @param whereClause
     * @param whereArgs
     * @return collection of locations
     */
    private Collection<BackgroundLocation> getLocations(String whereClause, String[] whereArgs) {
        Collection<BackgroundLocation> locations = new ArrayList<BackgroundLocation>();
        Cursor cursor = null;

        try {
            cursor = mResolver.query(
                    mContentUri,
                    null,
                    whereClause,
                    whereArgs,
                    LocationEntry.COLUMN_NAME_TIME + " ASC"
            );
            // v5.0.1 — ContentResolver.query() devuelve null si el proceso del provider
            // murio (el proceso :sync compite con el principal): NPE en produccion.
            if (cursor == null) {
                return locations;
            }
            while (cursor.moveToNext()) {
                locations.add(BackgroundLocation.fromCursor(cursor));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return locations;
    }

    @Override
    public Collection<BackgroundLocation> getAllLocations() {
        return getLocations(null, null);
    }

    @Override
    public Collection<BackgroundLocation> getValidLocations() {
        String whereClause = LocationEntry.COLUMN_NAME_STATUS + " <> ?";
        String[] whereArgs = { String.valueOf(BackgroundLocation.DELETED) };

        return getLocations(whereClause, whereArgs);
    }

    @Override
    public Collection<BackgroundLocation> getValidLocationsAndDelete() {
        // HM TODO: should be in a transaction but I'm not sure how to implement this...
        Collection<BackgroundLocation> locations = getValidLocations();
        deleteAllLocations();
        return locations;
    }

    @Override
    public BackgroundLocation getLocationById(long id) {
        BackgroundLocation location = null;

        Cursor cursor = null;
        try {
            cursor = mResolver.query(
                    LocationContentProvider.buildUriWithId(mAuthority, id),
                    null,
                    null,
                    null,
                    null
            );
            // v5.0.1 — ContentResolver.query() devuelve null si el proceso del provider
            // murio (el proceso :sync compite con el principal): NPE en produccion.
            if (cursor == null) {
                return location;
            }
            while (cursor.moveToNext()) {
                location = BackgroundLocation.fromCursor(cursor);
                if (!cursor.isLast()) {
                    throw new RuntimeException("Location " + id + " is not unique");
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return location;

    }

    public int getLocationsCount() {
        Cursor cursor = null;
        try {
            cursor = mResolver.query(
                    mContentUri,
                    new String[]{ "count(*)" },
                    null,
                    null,
                    null
            );
            if (cursor == null || !cursor.moveToFirst()) {
                return 0;
            }
            return cursor.getInt(0);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @Override
    public BackgroundLocation getFirstUnpostedLocation() {
        SqlSelectStatement subsql = new SqlSelectStatement();
        subsql.column(new SqlExpression(String.format("MIN(%s)", LocationEntry._ID)), LocationEntry._ID);
        subsql.from(LocationEntry.TABLE_NAME);
        subsql.where(LocationEntry.COLUMN_NAME_STATUS, SqlExpression.SqlOperatorEqualTo, BackgroundLocation.POST_PENDING);
        subsql.orderBy(LocationEntry.COLUMN_NAME_TIME);

        // NOTE: this builds a raw SQL sub-select and passes it as the provider's `selection`.
        // It is not injectable today — every value fed into SqlSelectStatement here is a numeric
        // constant or a long — but it depends on removeLastChar() stripping the trailing ';'
        // correctly, so keep it that way: never interpolate a String argument into this builder.
        String substmt = subsql.statement();
        substmt = com.marianhello.utils.TextUtils.removeLastChar(substmt, ";");

        BackgroundLocation location = null;
        Cursor cursor = null;
        try {
            cursor = mResolver.query(
                    mContentUri,
                    null,
                    LocationEntry._ID + " = (" + substmt + ")",
                    null,
                    null
                    );

            // v5.0.1 — ContentResolver.query() devuelve null si el proceso del provider
            // murio (el proceso :sync compite con el principal): NPE en produccion.
            if (cursor == null) {
                return location;
            }
            while (cursor.moveToNext()) {
                location = BackgroundLocation.fromCursor(cursor);
                if (!cursor.isLast()) {
                    throw new RuntimeException("Expected single location");
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return location;
    }

    @Override
    public BackgroundLocation getNextUnpostedLocation(long fromId) {
        SqlSelectStatement subsql = new SqlSelectStatement();
        subsql.column(new SqlExpression(String.format("MIN(%s)", LocationEntry._ID)), LocationEntry._ID);
        subsql.from(LocationEntry.TABLE_NAME);
        subsql.where(LocationEntry.COLUMN_NAME_STATUS, SqlExpression.SqlOperatorEqualTo, BackgroundLocation.POST_PENDING);
        subsql.where(LocationEntry._ID, SqlExpression.SqlOperatorNotEqualTo, fromId);
        subsql.orderBy(LocationEntry.COLUMN_NAME_TIME);

        // NOTE: this builds a raw SQL sub-select and passes it as the provider's `selection`.
        // It is not injectable today — every value fed into SqlSelectStatement here is a numeric
        // constant or a long — but it depends on removeLastChar() stripping the trailing ';'
        // correctly, so keep it that way: never interpolate a String argument into this builder.
        String substmt = subsql.statement();
        substmt = com.marianhello.utils.TextUtils.removeLastChar(substmt, ";");

        BackgroundLocation location = null;
        Cursor cursor = null;
        try {
            cursor = mResolver.query(
                    mContentUri,
                    null,
                    LocationEntry._ID + " = (" + substmt + ")",
                    null,
                    null
            );

            // v5.0.1 — ContentResolver.query() devuelve null si el proceso del provider
            // murio (el proceso :sync compite con el principal): NPE en produccion.
            if (cursor == null) {
                return location;
            }
            while (cursor.moveToNext()) {
                location = BackgroundLocation.fromCursor(cursor);
                if (!cursor.isLast()) {
                    throw new RuntimeException("Expected single location");
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return location;
    }

    @Override
    public long getUnpostedLocationsCount() {
        String whereClause = SQLiteLocationContract.LocationEntry.COLUMN_NAME_STATUS + " = ?";
        String[] whereArgs = { String.valueOf(BackgroundLocation.POST_PENDING) };

        // projection = count(*) instead of null: asking for every column of every matching row
        // just to call getCount() serialized the whole queue across Binder (multiple 2 MB
        // CursorWindows), and PostLocationTask does this on *every* fix. Also null-checked and
        // closed in a finally: ContentResolver.query returns null if the provider's process died.
        Cursor cursor = null;
        try {
            cursor = mResolver.query(
                    mContentUri,
                    new String[]{ "count(*)" },
                    whereClause,
                    whereArgs,
                    null);
            if (cursor == null || !cursor.moveToFirst()) {
                return 0;
            }
            return cursor.getInt(0);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @Override
    public long getLocationsForSyncCount(long millisSinceLastBatch) {
        String whereClause = TextUtils.join("", new String[]{
                SQLiteLocationContract.LocationEntry.COLUMN_NAME_STATUS + " = ? AND ( ",
                SQLiteLocationContract.LocationEntry.COLUMN_NAME_BATCH_START_MILLIS + " IS NULL OR ",
                SQLiteLocationContract.LocationEntry.COLUMN_NAME_BATCH_START_MILLIS + " < ? )",
        });
        String[] whereArgs = {
                String.valueOf(BackgroundLocation.SYNC_PENDING),
                String.valueOf(millisSinceLastBatch)
        };

        // projection = count(*) instead of null: asking for every column of every matching row
        // just to call getCount() serialized the whole queue across Binder (multiple 2 MB
        // CursorWindows), and PostLocationTask does this on *every* fix. Also null-checked and
        // closed in a finally: ContentResolver.query returns null if the provider's process died.
        Cursor cursor = null;
        try {
            cursor = mResolver.query(
                    mContentUri,
                    new String[]{ "count(*)" },
                    whereClause,
                    whereArgs,
                    null);
            if (cursor == null || !cursor.moveToFirst()) {
                return 0;
            }
            return cursor.getInt(0);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public Uri getOldestLocationUri() {
        Cursor cursor = null;
        try {
            cursor = mResolver.query(
                    mContentUri,
                    new String[]{"min(" + LocationEntry._ID + ")"},
                    TextUtils.join("", new String[]{
                            LocationEntry.COLUMN_NAME_TIME,
                            "= (SELECT min(",
                            LocationEntry.COLUMN_NAME_TIME,
                            ") FROM ",
                            LocationEntry.TABLE_NAME,
                            ")"
                    }),
                    null,
                    null
            );

            // v5.0.1 — ContentResolver.query() devuelve null si el proceso del provider
            // murio (el proceso :sync compite con el principal): NPE en produccion.
            // v5.0.1 — tabla vacia: moveToFirst() devolvia false y getLong(0) lanzaba
            // CursorIndexOutOfBoundsException.
            if (cursor == null || !cursor.moveToFirst() || cursor.isNull(0)) {
                return null;
            }
            return LocationContentProvider.buildUriWithId(mAuthority, cursor.getLong(0));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Persist location into database
     *
     * @param location
     * @return rowId or -1 when error occured
     */
    @Override
    public long persistLocation(BackgroundLocation location) {
        Uri locationUri = mResolver.insert(mContentUri, location.toContentValues());
        if (locationUri == null) {
            logger.error("ContentResolver.insert returned null, location not persisted");
            return -1;
        }
        // Long, not Integer: _ID is INTEGER PRIMARY KEY (64-bit) and Integer.valueOf would throw
        // NumberFormatException once the rowid passed 2^31.
        return Long.parseLong(locationUri.getLastPathSegment());
    }

    @Override
    public long persistLocation(BackgroundLocation location, int maxRows) {
        if (maxRows == 0) {
            return -1;
        }

        long rowCount = getLocationsCount();

        if (rowCount < maxRows) {
            return persistLocation(location);
        }

        // v5.0.1 — antes esto encolaba un DELETE de las (rowCount - maxRows) filas más antiguas
        // por `time` y, en el MISMO applyBatch, un UPDATE sobre getOldestLocationUri() — que es
        // justo una de las filas que el DELETE acababa de borrar. El UPDATE afectaba a 0 filas:
        // la posición no se guardaba y se devolvía el id de una fila inexistente, que el llamante
        // usaba luego para borrar/marcar. Ocurría siempre que la tabla superaba maxLocations (al
        // bajar el límite o al actualizar desde una BD ya crecida).
        //
        // Ahora: se recorta a maxRows - 1 y se inserta. El resultado final es el mismo (la tabla
        // queda exactamente en maxRows) sin depender de reciclar una fila concreta.
        long toDelete = rowCount - maxRows + 1;
        if (toDelete > 0) {
            String selection = new StringBuilder()
                    .append(LocationEntry._ID)
                    .append(" IN (SELECT ").append(LocationEntry._ID)
                    .append(" FROM ").append(LocationEntry.TABLE_NAME)
                    .append(" ORDER BY ").append(LocationEntry.COLUMN_NAME_TIME)
                    .append(" LIMIT ?)")
                    .toString();

            ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
            operations.add(
                    ContentProviderOperation.newDelete(mContentUri)
                    .withSelection(selection, new String[] { String.valueOf(toDelete) })
                    .build()
            );

            try {
                mResolver.applyBatch(mAuthority, operations);
            } catch (Exception e) {
                logger.error("Error trimming locations to maxRows {}: {}", maxRows, e.getMessage());
                return -1;
            }
        }

        return persistLocation(location);
    }

    @Override
    public long persistLocationForSync(BackgroundLocation location, int maxRows) {
        Long locationId = location.getLocationId();

        if (locationId == null) {
            location.setStatus(BackgroundLocation.SYNC_PENDING);
            return persistLocation(location, maxRows);
        } else {
            ContentValues values = new ContentValues();
            values.put(LocationEntry.COLUMN_NAME_STATUS, BackgroundLocation.SYNC_PENDING);

            String whereClause = LocationEntry._ID + " = ?";
            String[] whereArgs = { String.valueOf(locationId) };

            mResolver.update(mContentUri, values, whereClause, whereArgs);
            return locationId;
        }
    }

    @Override
    public void updateLocationForSync(long locationId) {
        ContentValues values = new ContentValues();
        values.put(LocationEntry.COLUMN_NAME_STATUS, BackgroundLocation.SYNC_PENDING);

        String whereClause = LocationEntry._ID + " = ?";
        String[] whereArgs = { String.valueOf(locationId) };

        mResolver.update(mContentUri, values, whereClause, whereArgs);
    }

    @Override
    /**
     * v5.0.1 — borrado LÓGICO, igual que {@code SQLiteLocationDAO.deleteLocationById} y que
     * {@code BatchManager.setBatchCompleted} (R1). Este DAO era el único que borraba físicamente:
     * un POST correcto en tiempo real hacía desaparecer la fila, así que {@code getLocations()} se
     * vaciaba igual que con el bug R1 pero por la ruta de tiempo real en vez de por la de lote.
     * `maxLocations` es lo que acota la tabla.
     *
     * <p>Un id negativo (persistLocation devolvió -1) construye una URI que el UriMatcher del
     * provider no reconoce -> IllegalArgumentException en el hilo del executor.
     */
    public void deleteLocationById(long locationId) {
        if (locationId < 0) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put(LocationEntry.COLUMN_NAME_STATUS, BackgroundLocation.DELETED);

        String whereClause = LocationEntry._ID + " = ?";
        String[] whereArgs = { String.valueOf(locationId) };

        mResolver.update(mContentUri, values, whereClause, whereArgs);
    }

    @Override
    public BackgroundLocation deleteFirstUnpostedLocation() {
        BackgroundLocation location = getFirstUnpostedLocation();
        // Returns null on an empty queue; dereferencing it here used to throw NPE.
        if (location == null) {
            return null;
        }
        deleteLocationById(location.getLocationId());

        return location;
    }

    @Override
    /**
     * v5.0.1 — borrado LÓGICO, igual que {@code SQLiteLocationDAO.deleteAllLocations}. El borrado
     * físico hacía que {@code getValidLocationsAndDelete()} destruyera también las filas
     * SYNC_PENDING que aún no se habían enviado: la app pedía "dame lo pendiente" y perdía lo que
     * no cabía en esa lectura.
     */
    public int deleteAllLocations() {
        ContentValues values = new ContentValues();
        values.put(LocationEntry.COLUMN_NAME_STATUS, BackgroundLocation.DELETED);

        return mResolver.update(mContentUri, values, null, null);
    }

    @Override
    public int deleteUnpostedLocations() {
        ContentValues values = new ContentValues();
        values.put(LocationEntry.COLUMN_NAME_STATUS, BackgroundLocation.SYNC_PENDING);

        String whereClause = LocationEntry.COLUMN_NAME_STATUS + " = ?";
        String[] whereArgs = { String.valueOf(BackgroundLocation.POST_PENDING) };

        return mResolver.update(mContentUri, values, whereClause, whereArgs);
    }

    @Override
    public int deletePendingSyncLocations() {
        ContentValues values = new ContentValues();
        values.put(LocationEntry.COLUMN_NAME_STATUS, BackgroundLocation.DELETED);

        String whereClause = LocationEntry.COLUMN_NAME_STATUS + " = ?";
        String[] whereArgs = { String.valueOf(BackgroundLocation.SYNC_PENDING) };

        return mResolver.update(mContentUri, values, whereClause, whereArgs);
    }
}
