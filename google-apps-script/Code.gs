/**
 * ============================================================
 *  PETTY CASH MANAGER — Google Apps Script
 *  Deploy as: Web App → Execute as ME → Anyone can access
 * ============================================================
 *
 *  SHEET STRUCTURE
 *  ───────────────
 *  MASTER SHEET  (private — never share this ID publicly)
 *    Sheet: "Companies"   → CompanyID | CompanyName | SheetID | CreatedAt | LogoUrl
 *    Sheet: "Users"       → UserID | CompanyID | Username | PasswordHash | Role | IsActive | CreatedAt
 *
 *  COMPANY SHEET  (one per company, auto-created)
 *    Sheet: "Transactions" → TransactionID | CompanyID | Type | Amount | Date |
 *                            Category | Description | BillImageUrl | AddedBy |
 *                            ApprovalStatus | ApprovedBy | CreatedAt
 *    Sheet: "Users"        → same structure (company-level copy for offline)
 *    Sheet: "Settings"     → Key | Value
 *
 * ============================================================
 */

// ── CONFIG ─────────────────────────────────────────────────────────────────
var MASTER_SHEET_ID = 'YOUR_MASTER_GOOGLE_SHEET_ID'; // ← Replace after creating Master Sheet
var DRIVE_FOLDER_NAME = 'PettyCash_BillImages';

// ── ENTRY POINTS ────────────────────────────────────────────────────────────

function doGet(e) {
  try {
    var action = e.parameter.action || '';
    var result;

    switch (action) {
      case 'setupCompany':   result = setupCompany(e.parameter);   break;
      case 'login':          result = login(e.parameter);          break;
      case 'getSheetId':     result = getSheetId(e.parameter);     break;
      case 'getTransactions':result = getTransactions(e.parameter);break;
      case 'getDashboard':   result = getDashboard(e.parameter);   break;
      case 'getUsers':       result = getUsers(e.parameter);       break;
      case 'exportData':     result = getTransactions(e.parameter);break;
      default:
        result = errorResponse('Unknown action: ' + action);
    }

    return buildResponse(result);
  } catch (err) {
    return buildResponse(errorResponse('Server error: ' + err.message));
  }
}

function doPost(e) {
  try {
    var params = e.parameter;
    var action = params.action || '';
    var result;

    switch (action) {
      case 'addTransaction':    result = addTransaction(params);    break;
      case 'updateApproval':    result = updateApproval(params);    break;
      case 'addUser':           result = addUser(params);           break;
      case 'uploadImage':       result = uploadImage(params);       break;
      case 'changePassword':    result = changePassword(params);    break;
      default:
        result = errorResponse('Unknown POST action: ' + action);
    }

    return buildResponse(result);
  } catch (err) {
    return buildResponse(errorResponse('Server error: ' + err.message));
  }
}

// ── RESPONSE BUILDERS ───────────────────────────────────────────────────────

function buildResponse(data) {
  return ContentService
    .createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}

function successResponse(data, message) {
  return { success: true, message: message || 'OK', data: data };
}

function errorResponse(message) {
  return { success: false, message: message, data: null };
}

// ── SECURITY HELPERS ────────────────────────────────────────────────────────

function validateCompanyId(companyId) {
  if (!companyId || companyId.trim() === '') return false;
  var master = SpreadsheetApp.openById(MASTER_SHEET_ID);
  var companiesSheet = master.getSheetByName('Companies');
  var data = companiesSheet.getDataRange().getValues();
  for (var i = 1; i < data.length; i++) {
    if (data[i][0] === companyId) return true;
  }
  return false;
}

function getCompanySheetId(companyId) {
  var master = SpreadsheetApp.openById(MASTER_SHEET_ID);
  var companiesSheet = master.getSheetByName('Companies');
  var data = companiesSheet.getDataRange().getValues();
  for (var i = 1; i < data.length; i++) {
    if (data[i][0] === companyId) return data[i][2]; // Column C = SheetID
  }
  return null;
}

function generateId() {
  return Utilities.getUuid();
}

// ── COMPANY SETUP ────────────────────────────────────────────────────────────

function setupCompany(params) {
  var companyName    = params.companyName    || '';
  var adminUsername  = params.adminUsername  || '';
  var adminPassHash  = params.adminPasswordHash || '';
  var logoUrl        = params.logoUrl        || '';

  if (!companyName || !adminUsername || !adminPassHash) {
    return errorResponse('Missing required fields');
  }

  // Check for duplicate company name
  var master = SpreadsheetApp.openById(MASTER_SHEET_ID);
  var companiesSheet = master.getSheetByName('Companies');
  var existingData = companiesSheet.getDataRange().getValues();
  for (var i = 1; i < existingData.length; i++) {
    if (existingData[i][1] === companyName) {
      return errorResponse('Company name already exists');
    }
  }

  var companyId = generateId();

  // Create a new Google Sheet for this company
  var newSheet = SpreadsheetApp.create('PettyCash_' + companyName + '_' + companyId.substring(0, 8));
  var sheetId  = newSheet.getId();

  // ── Set up Transactions tab ──────────────────────────────────────────────
  var txSheet = newSheet.getActiveSheet();
  txSheet.setName('Transactions');
  txSheet.appendRow([
    'TransactionID','CompanyID','Type','Amount','Date',
    'Category','Description','BillImageUrl','AddedBy',
    'ApprovalStatus','ApprovedBy','CreatedAt'
  ]);
  txSheet.getRange(1, 1, 1, 12).setFontWeight('bold')
         .setBackground('#1565C0').setFontColor('#FFFFFF');
  txSheet.setFrozenRows(1);

  // ── Set up Users tab ─────────────────────────────────────────────────────
  var usersSheet = newSheet.insertSheet('Users');
  usersSheet.appendRow([
    'UserID','CompanyID','Username','PasswordHash','Role','IsActive','CreatedAt'
  ]);
  usersSheet.getRange(1, 1, 1, 7).setFontWeight('bold')
            .setBackground('#1565C0').setFontColor('#FFFFFF');
  usersSheet.setFrozenRows(1);

  // Insert admin user into company Users sheet
  var adminUserId = generateId();
  usersSheet.appendRow([
    adminUserId, companyId, adminUsername, adminPassHash,
    'admin', true, new Date().toISOString()
  ]);

  // ── Set up Settings tab ──────────────────────────────────────────────────
  var settingsSheet = newSheet.insertSheet('Settings');
  settingsSheet.appendRow(['Key', 'Value']);
  settingsSheet.appendRow(['approval_enabled', 'false']);
  settingsSheet.appendRow(['logo_url', logoUrl]);
  settingsSheet.appendRow(['company_name', companyName]);
  settingsSheet.appendRow(['created_at', new Date().toISOString()]);

  // ── Register company in Master Sheet ─────────────────────────────────────
  companiesSheet.appendRow([
    companyId, companyName, sheetId,
    new Date().toISOString(), logoUrl
  ]);

  // Also add admin to master Users sheet
  var masterUsersSheet = master.getSheetByName('Users');
  if (!masterUsersSheet) {
    masterUsersSheet = master.insertSheet('Users');
    masterUsersSheet.appendRow([
      'UserID','CompanyID','Username','PasswordHash','Role','IsActive','CreatedAt'
    ]);
    masterUsersSheet.getRange(1,1,1,7).setFontWeight('bold')
                    .setBackground('#1565C0').setFontColor('#FFFFFF');
  }
  masterUsersSheet.appendRow([
    adminUserId, companyId, adminUsername, adminPassHash,
    'admin', true, new Date().toISOString()
  ]);

  return successResponse({
    companyId: companyId,
    sheetId: sheetId,
    adminUserId: adminUserId
  }, 'Company created successfully');
}

// ── LOGIN ────────────────────────────────────────────────────────────────────

function login(params) {
  var username    = params.username     || '';
  var passHash    = params.passwordHash || '';
  var companyId   = params.companyId    || '';

  if (!username || !passHash || !companyId) {
    return errorResponse('Missing credentials');
  }

  if (!validateCompanyId(companyId)) {
    return errorResponse('Invalid company ID');
  }

  // Look up in Master Users sheet
  var master = SpreadsheetApp.openById(MASTER_SHEET_ID);
  var usersSheet = master.getSheetByName('Users');
  if (!usersSheet) return errorResponse('User system not initialised');

  var data = usersSheet.getDataRange().getValues();
  var userRow = null;
  for (var i = 1; i < data.length; i++) {
    var row = data[i];
    // row: UserID(0) | CompanyID(1) | Username(2) | PasswordHash(3) | Role(4) | IsActive(5)
    if (row[2] === username && row[3] === passHash && row[1] === companyId && row[5] === true) {
      userRow = row;
      break;
    }
  }

  if (!userRow) {
    return errorResponse('Invalid username or password');
  }

  // Fetch company info
  var companiesSheet = master.getSheetByName('Companies');
  var compData = companiesSheet.getDataRange().getValues();
  var company = null;
  for (var j = 1; j < compData.length; j++) {
    if (compData[j][0] === companyId) {
      company = compData[j];
      break;
    }
  }

  if (!company) return errorResponse('Company not found');

  return {
    success:     true,
    message:     'Login successful',
    userId:      userRow[0],
    username:    userRow[2],
    role:        userRow[4],
    companyId:   company[0],
    companyName: company[1],
    sheetId:     company[2],
    logoUrl:     company[4] || ''
  };
}

// ── GET SHEET ID ─────────────────────────────────────────────────────────────

function getSheetId(params) {
  var companyId = params.companyId || '';
  if (!validateCompanyId(companyId)) return errorResponse('Invalid company ID');

  var sheetId = getCompanySheetId(companyId);
  if (!sheetId) return errorResponse('Sheet not found for company');

  return successResponse({ sheetId: sheetId });
}

// ── TRANSACTIONS ─────────────────────────────────────────────────────────────

function getTransactions(params) {
  var companyId  = params.companyId  || '';
  var startDate  = params.startDate  || '';
  var endDate    = params.endDate    || '';
  var category   = params.category   || '';

  if (!validateCompanyId(companyId)) return errorResponse('Invalid company ID');

  var sheetId = getCompanySheetId(companyId);
  if (!sheetId) return errorResponse('Company sheet not found');

  var ss = SpreadsheetApp.openById(sheetId);
  var txSheet = ss.getSheetByName('Transactions');
  if (!txSheet) return errorResponse('Transactions sheet not found');

  var data = txSheet.getDataRange().getValues();
  var transactions = [];

  for (var i = 1; i < data.length; i++) {
    var row = data[i];
    if (!row[0]) continue; // skip empty rows

    var txDate = row[4] ? row[4].toString() : '';

    // Date filter
    if (startDate && txDate < startDate) continue;
    if (endDate   && txDate > endDate)   continue;
    // Category filter
    if (category && row[5] !== category) continue;

    transactions.push({
      transactionId:  row[0],
      companyId:      row[1],
      type:           row[2],
      amount:         parseFloat(row[3]) || 0,
      date:           txDate,
      category:       row[5] || '',
      description:    row[6] || '',
      billImageUrl:   row[7] || '',
      addedBy:        row[8] || '',
      approvalStatus: row[9] || 'NOT_REQUIRED',
      approvedBy:     row[10] || '',
      createdAt:      row[11] ? new Date(row[11]).getTime() : 0,
      isSynced:       true
    });
  }

  // Sort newest first
  transactions.sort(function(a, b) {
    return b.date.localeCompare(a.date) || b.createdAt - a.createdAt;
  });

  return successResponse(transactions);
}

function addTransaction(params) {
  var companyId     = params.companyId     || '';
  var transactionId = params.transactionId || generateId();
  var type          = params.type          || '';
  var amount        = parseFloat(params.amount) || 0;
  var date          = params.date          || '';
  var category      = params.category      || '';
  var description   = params.description   || '';
  var billImageUrl  = params.billImageUrl  || '';
  var addedBy       = params.addedBy       || '';

  if (!validateCompanyId(companyId)) return errorResponse('Invalid company ID');
  if (!type || !amount || !date)      return errorResponse('Missing required fields');

  // Get approval setting from company sheet
  var sheetId = getCompanySheetId(companyId);
  if (!sheetId) return errorResponse('Company sheet not found');

  var ss = SpreadsheetApp.openById(sheetId);
  var txSheet = ss.getSheetByName('Transactions');

  // Check if transactionId already exists (idempotency)
  var existingData = txSheet.getDataRange().getValues();
  for (var i = 1; i < existingData.length; i++) {
    if (existingData[i][0] === transactionId) {
      return successResponse({ transactionId: transactionId }, 'Transaction already exists');
    }
  }

  // Determine approval status
  var settingsSheet = ss.getSheetByName('Settings');
  var approvalEnabled = false;
  if (settingsSheet) {
    var settings = settingsSheet.getDataRange().getValues();
    for (var s = 1; s < settings.length; s++) {
      if (settings[s][0] === 'approval_enabled') {
        approvalEnabled = settings[s][1] === 'true';
        break;
      }
    }
  }

  var approvalStatus = 'NOT_REQUIRED';
  if (type === 'EXPENSE' && approvalEnabled) {
    approvalStatus = 'PENDING';
  }

  var createdAt = new Date().toISOString();

  txSheet.appendRow([
    transactionId, companyId, type, amount, date,
    category, description, billImageUrl, addedBy,
    approvalStatus, '', createdAt
  ]);

  return successResponse({
    transactionId:  transactionId,
    companyId:      companyId,
    type:           type,
    amount:         amount,
    date:           date,
    category:       category,
    description:    description,
    billImageUrl:   billImageUrl,
    addedBy:        addedBy,
    approvalStatus: approvalStatus,
    approvedBy:     '',
    createdAt:      new Date(createdAt).getTime(),
    isSynced:       true
  }, 'Transaction added');
}

function updateApproval(params) {
  var companyId     = params.companyId     || '';
  var transactionId = params.transactionId || '';
  var status        = params.status        || '';
  var approvedBy    = params.approvedBy    || '';

  if (!validateCompanyId(companyId)) return errorResponse('Invalid company ID');
  if (!transactionId || !status)     return errorResponse('Missing fields');
  if (!['APPROVED','REJECTED'].includes(status)) return errorResponse('Invalid status');

  var sheetId = getCompanySheetId(companyId);
  if (!sheetId) return errorResponse('Sheet not found');

  var ss = SpreadsheetApp.openById(sheetId);
  var txSheet = ss.getSheetByName('Transactions');
  var data = txSheet.getDataRange().getValues();

  for (var i = 1; i < data.length; i++) {
    if (data[i][0] === transactionId && data[i][1] === companyId) {
      txSheet.getRange(i + 1, 10).setValue(status);      // ApprovalStatus (col J)
      txSheet.getRange(i + 1, 11).setValue(approvedBy);  // ApprovedBy (col K)
      return successResponse(null, 'Status updated to ' + status);
    }
  }

  return errorResponse('Transaction not found');
}

// ── DASHBOARD ────────────────────────────────────────────────────────────────

function getDashboard(params) {
  var companyId = params.companyId || '';
  if (!validateCompanyId(companyId)) return errorResponse('Invalid company ID');

  var sheetId = getCompanySheetId(companyId);
  if (!sheetId) return errorResponse('Sheet not found');

  var ss = SpreadsheetApp.openById(sheetId);
  var txSheet = ss.getSheetByName('Transactions');
  var data = txSheet.getDataRange().getValues();

  var totalCashIn  = 0;
  var totalExpense = 0;
  var pending      = 0;
  var count        = data.length - 1; // exclude header

  for (var i = 1; i < data.length; i++) {
    var row = data[i];
    if (!row[0]) continue;
    var type   = row[2];
    var amount = parseFloat(row[3]) || 0;
    var status = row[9];

    if (type === 'CASH_IN') {
      totalCashIn += amount;
    } else if (type === 'EXPENSE') {
      if (status === 'APPROVED' || status === 'NOT_REQUIRED') {
        totalExpense += amount;
      }
      if (status === 'PENDING') pending++;
    }
  }

  return successResponse({
    totalCashIn:      totalCashIn,
    totalExpense:     totalExpense,
    currentBalance:   totalCashIn - totalExpense,
    pendingApprovals: pending,
    transactionCount: count
  });
}

// ── USERS ────────────────────────────────────────────────────────────────────

function getUsers(params) {
  var companyId = params.companyId || '';
  if (!validateCompanyId(companyId)) return errorResponse('Invalid company ID');

  var sheetId = getCompanySheetId(companyId);
  if (!sheetId) return errorResponse('Sheet not found');

  var ss = SpreadsheetApp.openById(sheetId);
  var usersSheet = ss.getSheetByName('Users');
  if (!usersSheet) return successResponse([]);

  var data = usersSheet.getDataRange().getValues();
  var users = [];
  for (var i = 1; i < data.length; i++) {
    var row = data[i];
    if (!row[0]) continue;
    users.push({
      userId:    row[0],
      companyId: row[1],
      username:  row[2],
      // Never return password hash
      role:      row[4],
      isActive:  row[5],
      createdAt: new Date(row[6]).getTime()
    });
  }
  return successResponse(users);
}

function addUser(params) {
  var companyId    = params.companyId    || '';
  var userId       = params.userId       || generateId();
  var username     = params.username     || '';
  var passwordHash = params.passwordHash || '';
  var role         = params.role         || 'employee';

  if (!validateCompanyId(companyId)) return errorResponse('Invalid company ID');
  if (!username || !passwordHash)    return errorResponse('Missing fields');

  // Add to Master Users
  var master = SpreadsheetApp.openById(MASTER_SHEET_ID);
  var masterUsers = master.getSheetByName('Users');

  // Check duplicate username in same company
  var masterData = masterUsers.getDataRange().getValues();
  for (var i = 1; i < masterData.length; i++) {
    if (masterData[i][2] === username && masterData[i][1] === companyId) {
      return errorResponse('Username already exists in this company');
    }
  }

  var now = new Date().toISOString();
  masterUsers.appendRow([userId, companyId, username, passwordHash, role, true, now]);

  // Also add to Company Users sheet
  var sheetId = getCompanySheetId(companyId);
  if (sheetId) {
    var ss = SpreadsheetApp.openById(sheetId);
    var companyUsers = ss.getSheetByName('Users');
    if (companyUsers) {
      companyUsers.appendRow([userId, companyId, username, passwordHash, role, true, now]);
    }
  }

  return successResponse({
    userId: userId, companyId: companyId,
    username: username, role: role, isActive: true
  }, 'User added');
}

function changePassword(params) {
  var companyId       = params.companyId       || '';
  var username        = params.username        || '';
  var oldPasswordHash = params.oldPasswordHash || '';
  var newPasswordHash = params.newPasswordHash || '';

  if (!validateCompanyId(companyId)) return errorResponse('Invalid company ID');

  var master = SpreadsheetApp.openById(MASTER_SHEET_ID);
  var usersSheet = master.getSheetByName('Users');
  var data = usersSheet.getDataRange().getValues();

  for (var i = 1; i < data.length; i++) {
    if (data[i][2] === username && data[i][1] === companyId && data[i][3] === oldPasswordHash) {
      usersSheet.getRange(i + 1, 4).setValue(newPasswordHash);
      return successResponse(null, 'Password changed');
    }
  }

  return errorResponse('Current password is incorrect');
}

// ── IMAGE UPLOAD ─────────────────────────────────────────────────────────────

function uploadImage(params) {
  var companyId   = params.companyId   || '';
  var imageBase64 = params.imageBase64 || '';
  var fileName    = params.fileName    || ('bill_' + Date.now() + '.jpg');

  if (!validateCompanyId(companyId)) return errorResponse('Invalid company ID');
  if (!imageBase64)                  return errorResponse('No image data');

  try {
    // Find or create the Drive folder for this company
    var folderName = DRIVE_FOLDER_NAME + '_' + companyId.substring(0, 8);
    var folders = DriveApp.getFoldersByName(folderName);
    var folder;
    if (folders.hasNext()) {
      folder = folders.next();
    } else {
      folder = DriveApp.createFolder(folderName);
      folder.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
    }

    // Decode base64 and save
    var decoded = Utilities.base64Decode(imageBase64);
    var blob    = Utilities.newBlob(decoded, 'image/jpeg', fileName);
    var file    = folder.createFile(blob);
    file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);

    var fileUrl = 'https://drive.google.com/uc?export=view&id=' + file.getId();

    return successResponse({ url: fileUrl, fileId: file.getId() }, 'Image uploaded');
  } catch (err) {
    return errorResponse('Image upload failed: ' + err.message);
  }
}

// ── INIT MASTER SHEET (run once manually) ────────────────────────────────────

function initMasterSheet() {
  var ss = SpreadsheetApp.openById(MASTER_SHEET_ID);

  // Companies sheet
  var companiesSheet = ss.getSheetByName('Companies');
  if (!companiesSheet) {
    companiesSheet = ss.insertSheet('Companies');
  }
  if (companiesSheet.getLastRow() === 0) {
    companiesSheet.appendRow(['CompanyID','CompanyName','SheetID','CreatedAt','LogoUrl']);
    companiesSheet.getRange(1,1,1,5).setFontWeight('bold')
                  .setBackground('#1565C0').setFontColor('#FFFFFF');
    companiesSheet.setFrozenRows(1);
  }

  // Users sheet
  var usersSheet = ss.getSheetByName('Users');
  if (!usersSheet) {
    usersSheet = ss.insertSheet('Users');
  }
  if (usersSheet.getLastRow() === 0) {
    usersSheet.appendRow(['UserID','CompanyID','Username','PasswordHash','Role','IsActive','CreatedAt']);
    usersSheet.getRange(1,1,1,7).setFontWeight('bold')
              .setBackground('#1565C0').setFontColor('#FFFFFF');
    usersSheet.setFrozenRows(1);
    // Protect the sheet from being viewed except by owner
    var protection = usersSheet.protect().setDescription('Master Users — Admin only');
    protection.removeEditors(protection.getEditors());
  }

  SpreadsheetApp.getUi().alert('Master Sheet initialised successfully!');
}
