import json
import boto3
import uuid

dynamodb = boto3.resource('dynamodb')
table = dynamodb.Table('Login')  # Changed from 'UsersTable' to 'Login' to match Java app

def lambda_handler(event, context):
    method = event['httpMethod']
    path = event['path']

    if method == 'POST' and path == '/users':
        return register_user(event)
    elif method == 'GET' and path.startswith('/users/'):
        return get_user(event)
    else:
        return {'statusCode': 400, 'body': json.dumps('Invalid Request')}

def register_user(event):
    body = json.loads(event['body'])
    username = body.get('username')  # Added username field
    email = body.get('email')
    password = body.get('password')  # Added password field

    # Check if email already exists
    response = table.query(
        KeyConditionExpression=boto3.dynamodb.conditions.Key('email').eq(email)
    )
    
    if response.get('Items'):
        return {
            'statusCode': 409,
            'body': json.dumps({'message': 'Email already registered'})
        }
    
    item = {
        'email': email,  # Using email as primary key
        'username': username,
        'password': password
    }

    table.put_item(Item=item)

    return {
        'statusCode': 201,
        'body': json.dumps({'message': 'User registered', 'email': email})
    }

def get_user(event):
    email = event['pathParameters']['id']

    response = table.get_item(Key={'email': email})
    item = response.get('Item')

    if item:
        # Don't send password back to client
        if 'password' in item:
            del item['password']
        return {'statusCode': 200, 'body': json.dumps(item)}
    else:
        return {'statusCode': 404, 'body': json.dumps('User not found')}
