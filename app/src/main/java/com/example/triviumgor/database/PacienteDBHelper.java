package com.example.triviumgor.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PacienteDBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "trivium.db";
    private static final int DATABASE_VERSION = 1;
    private static String DATABASE_PATH;
    private final Context mContext;

    // Nombre de tabla y columnas (mantén tus definiciones existentes)
    public static final String TABLE_PACIENTES = "pacientes";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_CIC = "cic";
    public static final String COLUMN_DNI = "dni";
    public static final String COLUMN_NOMBRE = "nombre";
    public static final String COLUMN_APELLIDO1 = "apellido1";
    public static final String COLUMN_APELLIDO2 = "apellido2";
    public static final String COLUMN_PATOLOGIA = "patología";
    public static final String COLUMN_MEDICACIÓN = "medicación";
    public static final String COLUMN_INTENSIDAD = "intensidad";
    public static final String COLUMN_TIEMPO = "tiempo";
    public static final String COLUMN_INTENSIDAD2 = "intensidad2";
    public static final String COLUMN_TIEMPO2 = "tiempo2";

    // NUEVA Tabla USUARIOS
    public static final String TABLE_USUARIOS = "usuarios";
    public static final String COLUMN_USUARIO_ID = "_id";
    public static final String COLUMN_USERNAME = "username";
    public static final String COLUMN_PASSWORD_HASH = "password_hash";
    public static final String COLUMN_NOMBRE_COMPLETO = "nombre_completo";
    public static final String COLUMN_ROL = "rol"; // admin, medico, enfermero, etc.
    public static final String COLUMN_ACTIVO = "activo"; // 1 = activo, 0 = inactivo
    public static final String COLUMN_FECHA_CREACION = "fecha_creacion";
    public static final String COLUMN_ULTIMO_ACCESO = "ultimo_acceso";


    // Nueva tabla de sesiones
    public static final String TABLE_SESIONES = "sesiones";
    public static final String COLUMN_SESION_ID = "_id";
    public static final String COLUMN_PACIENTE_ID = "paciente_id";
    public static final String COLUMN_DISPOSITIVO = "dispositivo";
    public static final String COLUMN_FECHA = "fecha";
    public static final String COLUMN_INTENSIDAD_SESION = "intensidad";
    public static final String COLUMN_TIEMPO_SESION = "tiempo";


    // Sentencia SQL para crear la tabla
    private static final String SQL_CREATE_PACIENTES =
            "CREATE TABLE " + TABLE_PACIENTES + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_CIC + " TEXT, " +
                    COLUMN_DNI + " TEXT NOT NULL, " +
                    COLUMN_NOMBRE + " TEXT NOT NULL, " +
                    COLUMN_APELLIDO1 + " TEXT NOT NULL, " +
                    COLUMN_APELLIDO2 + " TEXT, " +
                    COLUMN_PATOLOGIA + " TEXT, " +
                    COLUMN_MEDICACIÓN + " TEXT," +
                    COLUMN_INTENSIDAD + " INTEGER, " +
                    COLUMN_TIEMPO + " INTEGER, " +
                    COLUMN_INTENSIDAD2 + " INTEGER DEFAULT 0, " +
                    COLUMN_TIEMPO2 + " INTEGER DEFAULT 0)";

    // SQL para crear tabla USUARIOS
    private static final String SQL_CREATE_USUARIOS =
            "CREATE TABLE " + TABLE_USUARIOS + " (" +
                    COLUMN_USUARIO_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_USERNAME + " TEXT UNIQUE NOT NULL, " +
                    COLUMN_PASSWORD_HASH + " TEXT NOT NULL, " +
                    COLUMN_NOMBRE_COMPLETO + " TEXT, " +
                    COLUMN_ROL + " TEXT DEFAULT 'medico', " +
                    COLUMN_ACTIVO + " INTEGER DEFAULT 1, " +
                    COLUMN_FECHA_CREACION + " TEXT NOT NULL, " +
                    COLUMN_ULTIMO_ACCESO + " TEXT)";


    // Sentencia SQL para crear la tabla sesiones
    private static final String SQL_CREATE_SESIONES =
            "CREATE TABLE " + TABLE_SESIONES + " (" +
                    COLUMN_SESION_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_PACIENTE_ID + " INTEGER NOT NULL, " +
                    COLUMN_DISPOSITIVO + " TEXT NOT NULL, " +
                    COLUMN_FECHA + " TEXT NOT NULL, " +
                    COLUMN_INTENSIDAD_SESION + " INTEGER NOT NULL, " +
                    COLUMN_TIEMPO_SESION + " INTEGER NOT NULL, " +
                    "FOREIGN KEY (" + COLUMN_PACIENTE_ID + ") REFERENCES " +
                    TABLE_PACIENTES + "(" + COLUMN_ID + "))";

    // Constructor modificado
    public PacienteDBHelper(Context context) {
        super(context, getDatabasePath(context), null, DATABASE_VERSION);
        mContext = context;
        DATABASE_PATH = getDatabasePath(context);

        checkExistingDatabase();
    }

    // Método para obtener la ruta de la base de datos en almacenamiento externo
    private static String getDatabasePath(Context context) {
        // Verificar si el almacenamiento externo está disponible para lectura y escritura
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            Log.e("PacienteDBHelper", "Almacenamiento externo no disponible");
            // Fallback a almacenamiento interno si el externo no está disponible
            return context.getDatabasePath(DATABASE_NAME).getPath();
        }

        // Crear directorio para la base de datos si no existe
        File dbDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "TriviumData");

        if (!dbDir.exists()) {
            if (!dbDir.mkdirs()) {
                Log.e("PacienteDBHelper", "No se pudo crear directorio para la base de datos");
                // Fallback a almacenamiento interno si no se puede crear directorio
                return context.getDatabasePath(DATABASE_NAME).getPath();
            }
        }

        // Ruta completa al archivo de base de datos
        return new File(dbDir, DATABASE_NAME).getAbsolutePath();
    }

    public void checkExistingDatabase() {
        File dbFile = new File(DATABASE_PATH);
        if (dbFile.exists()) {
            Log.d("PacienteDBHelper", "Base de datos encontrada en almacenamiento externo");
        } else {
            Log.d("PacienteDBHelper", "No se encontró base de datos existente, creando nueva");
        }
    }



    @Override
    public void onCreate(SQLiteDatabase db) {
        // Este método se llama cuando la base de datos se crea por primera vez
        db.execSQL(SQL_CREATE_PACIENTES);
        db.execSQL(SQL_CREATE_SESIONES);
        db.execSQL(SQL_CREATE_USUARIOS);

        // Insertar usuario administrador por defecto
        insertarUsuarioAdmin(db);

        //insertarPacientesIniciales(db);  //esto es para que no te salga lleno
        Log.d("PacienteDBHelper", "Base de datos creada en: " + DATABASE_PATH);
    }
/*
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Este método se llama cuando se actualiza la versión de la base de datos
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PACIENTES);
        onCreate(db);
    }*/
@Override
public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    // Este método se llama cuando se actualiza la versión de la base de datos
    if (oldVersion < 2) {
        // Actualización de versión 1 a 2: crear tabla sesiones y usuarios
        try {
            db.execSQL(SQL_CREATE_SESIONES);
            Log.d("PacienteDBHelper", "Tabla sesiones creada en actualización de base de datos");
        } catch (Exception e) {
            Log.e("PacienteDBHelper", "Error al crear tabla sesiones: " + e.getMessage());
        }

        try {
            db.execSQL(SQL_CREATE_USUARIOS);
            insertarUsuarioAdmin(db);
            Log.d("PacienteDBHelper", "Tabla usuarios creada en actualización de base de datos");
        } catch (Exception e) {
            Log.e("PacienteDBHelper", "Error al crear tabla usuarios: " + e.getMessage());
        }
    }
}

    /**
     * Inserta un usuario administrador por defecto
     */
    private void insertarUsuarioAdmin(SQLiteDatabase db) {
        try {
            ContentValues values = new ContentValues();
            values.put(COLUMN_USERNAME, "admin");
            values.put(COLUMN_PASSWORD_HASH, hashPassword("admin123")); // Contraseña: admin123
            values.put(COLUMN_NOMBRE_COMPLETO, "Administrador");
            values.put(COLUMN_ROL, "admin");
            values.put(COLUMN_ACTIVO, 1);
            values.put(COLUMN_FECHA_CREACION, getCurrentDateTime());

            long id = db.insert(TABLE_USUARIOS, null, values);

            if (id != -1) {
                Log.d("PacienteDBHelper", "Usuario administrador creado con éxito");
            } else {
                Log.e("PacienteDBHelper", "Error al crear usuario administrador");
            }
        } catch (Exception e) {
            Log.e("PacienteDBHelper", "Error al insertar usuario admin: " + e.getMessage());
        }
    }

    /**
     * Genera hash SHA-256 de una contraseña
     */
    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e("PacienteDBHelper", "Error al hashear contraseña: " + e.getMessage());
            return password; // Fallback (NO RECOMENDADO EN PRODUCCIÓN)
        }
    }

    /**
     * Obtiene la fecha y hora actual en formato ISO
     */
    private String getCurrentDateTime() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.getDefault()).format(new java.util.Date());
    }



    private void insertarPacientesIniciales(SQLiteDatabase db) {
        // Array con los datos de los 50 pacientes
        String[][] pacientesDatos = {
                {"CIC173982", "72564981K", "María", "García", "López", "Cervicalgia, Tendinitis del supraespinoso", "Ibuprofeno 600mg, Paracetamol 1g", "10", "15"},
                {"CIC284561", "18745632S", "Juan", "Martínez", "Sánchez", "Lumbalgia crónica", "Diclofenaco 50mg, Ciclobenzaprina 10mg", "8", "20"},
                {"CIC395672", "34567821D", "Ana", "Fernández", "Rodríguez", "Epicondilitis lateral, Síndrome del túnel carpiano", "Naproxeno 500mg", "7", "15"},
                {"CIC426731", "25896314F", "Carlos", "González", "Pérez", "Artrosis de rodilla", "Paracetamol 1g, Condroitín sulfato", "9", "25"},
                {"CIC538290", "65432198G", "Laura", "Díaz", "Moreno", "Fascitis plantar", "Ibuprofeno 600mg", "6", "20"},
                {"CIC619483", "78965412H", "Jorge", "Ruiz", "Gutiérrez", "Hombro congelado, Cervicalgia", "Tramadol 50mg, Pregabalina 75mg", "12", "30"},
                {"CIC723901", "14725836J", "Lucía", "Serrano", "Ortega", "Fibromialgia", "Duloxetina 60mg, Amitriptilina 25mg", "8", "25"},
                {"CIC834672", "96385274K", "Javier", "Hernández", "Molina", "Dolor neuropático", "Gabapentina 300mg", "10", "20"},
                {"CIC945781", "75395146L", "Carmen", "Jiménez", "Torres", "Tendinopatía rotuliana", "Etoricoxib 90mg", "7", "15"},
                {"CIC103928", "36925814Z", "Miguel", "Castro", "Flores", "Síndrome del piriforme, Dolor lumbar crónico", "Metamizol 575mg, Ciclobenzaprina 10mg", "9", "20"},
                {"CIC112843", "25814736P", "Elena", "Sanz", "Blanco", "Radiculopatía cervical", "Pregabalina 75mg, Paracetamol 1g", "11", "25"},
                {"CIC127349", "14702583Q", "Pablo", "Ortiz", "Navarro", "Esguince de tobillo", "Ibuprofeno 600mg", "6", "15"},
                {"CIC138265", "36985214R", "Marta", "Martín", "Vega", "Bursitis trocantérea", "Naproxeno 500mg", "8", "20"},
                {"CIC149375", "25874136S", "Daniel", "Romero", "Ramos", "Contractura muscular", "Diclofenaco 50mg", "7", "15"},
                {"CIC156492", "98765432T", "Sofía", "Alonso", "Silva", "Neuralgia del trigémino", "Pregabalina 75mg, Amitriptilina 25mg", "13", "25"},
                {"CIC167583", "45612378U", "Alberto", "Morales", "Santos", "Síndrome de dolor miofascial", "Ciclobenzaprina 10mg", "9", "20"},
                {"CIC178264", "78945612V", "Cristina", "Gil", "Cruz", "Osteoporosis", "Calcio, Vitamina D", "6", "15"},
                {"CIC189375", "12378945W", "Fernando", "Méndez", "Prieto", "Artritis reumatoide", "Metotrexato 2.5mg, Prednisona 5mg", "10", "25"},
                {"CIC193847", "36914725X", "Raquel", "Campos", "Calvo", "Cervicalgia, Contractura muscular", "Ibuprofeno 600mg, Ciclobenzaprina 10mg", "8", "20"},
                {"CIC207493", "25836914Y", "David", "Vidal", "Herrera", "Fascitis plantar, Esguince de tobillo", "Naproxeno 500mg", "7", "15"},
                {"CIC218465", "93265814L", "Patricia", "Garrido", "Medina", "Síndrome del túnel carpiano", "Paracetamol 1g", "6", "20"},
                {"CIC227349", "41596357C", "Alejandro", "Aguilar", "León", "Tendinitis del supraespinoso, Hombro congelado", "Etoricoxib 90mg, Tramadol 50mg", "14", "30"},
                {"CIC238954", "97531246Z", "Beatriz", "Peña", "Márquez", "Dolor lumbar crónico", "Diclofenaco 50mg", "8", "20"},
                {"CIC249385", "16482973G", "Rubén", "Bravo", "Gallego", "Artrosis de rodilla", "Condroitín sulfato, Glucosamina", "9", "25"},
                {"CIC251763", "28974615N", "Natalia", "Soto", "Lara", "Fibromialgia, Síndrome de dolor miofascial", "Duloxetina 60mg, Pregabalina 75mg", "12", "30"},
                {"CIC269384", "34781965B", "Eduardo", "Velasco", "Soler", "Epicondilitis lateral", "Ibuprofeno 600mg", "7", "15"},
                {"CIC273854", "95682317M", "Marina", "Moya", "Guerrero", "Tendinopatía rotuliana", "Naproxeno 500mg", "8", "20"},
                {"CIC289465", "26749513F", "Gonzalo", "Cano", "Iglesias", "Neuralgia del trigémino", "Pregabalina 75mg, Gabapentina 300mg", "11", "25"},
                {"CIC297514", "73915824J", "Silvia", "Delgado", "Sáez", "Radiculopatía cervical", "Tramadol 50mg", "10", "20"},
                {"CIC301785", "39174628A", "Marcos", "Castillo", "Vargas", "Bursitis trocantérea", "Dexketoprofeno 25mg", "6", "15"},
                {"CIC316284", "19873456D", "Sandra", "Rivas", "Duran", "Síndrome del piriforme", "Ciclobenzaprina 10mg", "7", "20"},
                {"CIC329486", "64398721E", "Roberto", "Pascual", "Hidalgo", "Dolor neuropático, Cervicalgia", "Gabapentina 300mg, Paracetamol 1g", "13", "25"},
                {"CIC337162", "81729364H", "Alicia", "Fuentes", "Ferrer", "Contractura muscular", "Diclofenaco 50mg", "8", "15"},
                {"CIC347591", "23478156I", "Iván", "Cabrera", "Suárez", "Artritis reumatoide", "Metotrexato 2.5mg, Hidroxicloroquina 200mg", "9", "20"},
                {"CIC358274", "92817364K", "Teresa", "Carrasco", "Nieto", "Esguince de tobillo", "Ibuprofeno 600mg", "6", "15"},
                {"CIC369487", "34796158P", "Emilio", "Reyes", "Caballero", "Fascitis plantar", "Naproxeno 500mg", "7", "20"},
                {"CIC376215", "76543219R", "Carolina", "Santana", "Parra", "Osteoporosis", "Calcio, Vitamina D", "5", "15"},
                {"CIC386542", "17293548S", "Óscar", "Herrero", "Benítez", "Dolor lumbar crónico, Síndrome de dolor miofascial", "Tramadol 50mg, Ciclobenzaprina 10mg", "12", "30"},
                {"CIC397412", "45781236T", "Julia", "Esteban", "Carmona", "Tendinitis del supraespinoso", "Etoricoxib 90mg", "8", "20"},
                {"CIC407832", "36149275U", "Hugo", "Sierra", "Rivera", "Artrosis de rodilla", "Condroitín sulfato, Paracetamol 1g", "9", "25"},
                {"CIC418295", "93456781V", "Carla", "Rojas", "Pastor", "Síndrome del túnel carpiano", "Ibuprofeno 600mg", "6", "15"},
                {"CIC427136", "71495632W", "Mario", "Molina", "Soria", "Fibromialgia", "Duloxetina 60mg, Pregabalina 75mg", "15", "30"},
                {"CIC438657", "25943678X", "Nuria", "Salas", "Rubio", "Epicondilitis lateral", "Naproxeno 500mg", "7", "20"},
                {"CIC449382", "81947365Y", "Samuel", "Roca", "Luque", "Cervicalgia", "Diclofenaco 50mg", "8", "15"},
                {"CIC453871", "43156789A", "Claudia", "Vera", "Manzano", "Radiculopatía cervical", "Tramadol 50mg, Paracetamol 1g", "11", "25"},
                {"CIC468274", "17658923B", "Antonio", "Bernal", "Lozano", "Hombro congelado", "Etoricoxib 90mg", "10", "30"},
                {"CIC479364", "36598741C", "Isabel", "Vila", "Montero", "Tendinopatía rotuliana", "Ibuprofeno 600mg", "7", "15"},
                {"CIC481935", "95687412D", "José", "Andreu", "Cortes", "Bursitis trocantérea", "Naproxeno 500mg", "8", "20"},
                {"CIC492846", "23476581E", "Mónica", "Gómez", "Pastor", "Dolor neuropático", "Gabapentina 300mg, Pregabalina 75mg", "13", "25"},
                {"CIC503716", "51982347F", "Raúl", "Muñoz", "Domínguez", "Síndrome del piriforme, Dolor lumbar crónico", "Tramadol 50mg, Ciclobenzaprina 10mg", "14", "30"}
        };

        // Insertar cada paciente en la base de datos
        for (String[] datos : pacientesDatos) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_CIC, datos[0]);
            values.put(COLUMN_DNI, datos[1]);
            values.put(COLUMN_NOMBRE, datos[2]);
            values.put(COLUMN_APELLIDO1, datos[3]);
            values.put(COLUMN_APELLIDO2, datos[4]);
            values.put(COLUMN_PATOLOGIA, datos[5]);
            values.put(COLUMN_MEDICACIÓN, datos[6]);
            values.put(COLUMN_INTENSIDAD, Integer.parseInt(datos[7]));
            values.put(COLUMN_TIEMPO, Integer.parseInt(datos[8]));

            long id = db.insert(TABLE_PACIENTES, null, values);
            if (id != -1) {
                Log.d("PacienteDBHelper", "Paciente insertado: " + datos[2] + " " + datos[3] + " (ID: " + id + ")");
            } else {
                Log.e("PacienteDBHelper", "Error al insertar paciente: " + datos[2] + " " + datos[3]);
            }
        }

        Log.d("PacienteDBHelper", "Inserción de pacientes completada");

    }

}
